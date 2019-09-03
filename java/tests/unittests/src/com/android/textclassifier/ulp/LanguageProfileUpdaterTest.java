/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.textclassifier.ulp;

import static com.google.common.truth.Truth.assertThat;

import android.app.Person;
import android.content.Context;
import android.os.Bundle;
import android.view.textclassifier.ConversationActions;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.textclassifier.ulp.database.LanguageProfileDatabase;
import com.android.textclassifier.ulp.database.LanguageSignalInfo;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** Testing {@link LanguageProfileUpdater} in an inMemoryDatabase */
@SmallTest
public class LanguageProfileUpdaterTest {

    private static final String NOTIFICATION_KEY = "test_notification";
    private static final String LANGUAGE_TAG_EN = Locale.ENGLISH.toLanguageTag();
    private static final String LANGUAGE_TAG_ZH = Locale.CHINESE.toLanguageTag();
    private static final String TEXT_ONE = "hello world";
    private static final String TEXT_TWO = "你好!";
    private static final Function<CharSequence, List<String>> LANGUAGE_DETECTOR_EN =
            charSequence -> Collections.singletonList(LANGUAGE_TAG_EN);
    private static final Function<CharSequence, List<String>> LANGUAGE_DETECTOR_ZH =
            charSequence -> Collections.singletonList(LANGUAGE_TAG_ZH);
    private static final Person PERSON = new Person.Builder().build();
    private static final ZonedDateTime TIME_ONE =
            ZonedDateTime.of(2019, 7, 21, 12, 12, 12, 12, ZoneId.systemDefault());
    private static final ZonedDateTime TIME_TWO =
            ZonedDateTime.of(2019, 7, 21, 12, 20, 20, 12, ZoneId.systemDefault());
    private static final ConversationActions.Message MSG_ONE =
            new ConversationActions.Message.Builder(PERSON)
                    .setReferenceTime(TIME_ONE)
                    .setText(TEXT_ONE)
                    .setExtras(new Bundle())
                    .build();
    private static final ConversationActions.Message MSG_TWO =
            new ConversationActions.Message.Builder(PERSON)
                    .setReferenceTime(TIME_TWO)
                    .setText("where are you?")
                    .setExtras(new Bundle())
                    .build();
    private static final ConversationActions.Message MSG_THREE =
            new ConversationActions.Message.Builder(PERSON)
                    .setReferenceTime(TIME_TWO)
                    .setText(TEXT_TWO)
                    .setExtras(new Bundle())
                    .build();
    private static final ConversationActions.Request CONVERSATION_ACTION_REQUEST_ONE =
            new ConversationActions.Request.Builder(Arrays.asList(MSG_ONE)).build();
    private static final ConversationActions.Request CONVERSATION_ACTION_REQUEST_TWO =
            new ConversationActions.Request.Builder(Arrays.asList(MSG_TWO)).build();
    private static final LanguageSignalInfo US_INFO_ONE_FOR_CONVERSATION_ACTION_ONE =
            new LanguageSignalInfo(
                    LANGUAGE_TAG_EN, LanguageSignalInfo.SUGGEST_CONVERSATION_ACTIONS, 1);
    private static final LanguageSignalInfo US_INFO_ONE_FOR_CONVERSATION_ACTION_TWO =
            new LanguageSignalInfo(
                    LANGUAGE_TAG_EN, LanguageSignalInfo.SUGGEST_CONVERSATION_ACTIONS, 2);

    private LanguageProfileUpdater mLanguageProfileUpdater;
    private LanguageProfileDatabase mDatabase;

    @Before
    public void setup() {
        Context mContext = ApplicationProvider.getApplicationContext();
        ListeningExecutorService mExecutorService =
                MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mDatabase = Room.inMemoryDatabaseBuilder(mContext, LanguageProfileDatabase.class).build();
        mLanguageProfileUpdater = new LanguageProfileUpdater(mExecutorService, mDatabase);
    }

    @After
    public void close() {
        mDatabase.close();
    }

    @Test
    public void updateFromConversationActionsAsync_oneMessage()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(
                        CONVERSATION_ACTION_REQUEST_ONE, LANGUAGE_DETECTOR_EN)
                .get();
        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();

        assertThat(infos).hasSize(1);
        LanguageSignalInfo info = infos.get(0);
        assertThat(info).isEqualTo(US_INFO_ONE_FOR_CONVERSATION_ACTION_ONE);
    }

    /** Notification keys for these two messages are DEFAULT_NOTIFICATION_KEY */
    @Test
    public void updateFromConversationActionsAsync_twoMessagesInSameNotificationWithSameLanguage()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(
                        CONVERSATION_ACTION_REQUEST_ONE, LANGUAGE_DETECTOR_EN)
                .get();
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(
                        CONVERSATION_ACTION_REQUEST_TWO, LANGUAGE_DETECTOR_EN)
                .get();
        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();

        assertThat(infos).hasSize(1);
        LanguageSignalInfo info = infos.get(0);
        assertThat(info).isEqualTo(US_INFO_ONE_FOR_CONVERSATION_ACTION_TWO);
    }

    @Test
    public void updateFromConversationActionsAsync_twoMessagesInDifferentNotifications()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(
                        CONVERSATION_ACTION_REQUEST_ONE, LANGUAGE_DETECTOR_EN)
                .get();
        Bundle extra = new Bundle();
        extra.putString(LanguageProfileUpdater.NOTIFICATION_KEY, NOTIFICATION_KEY);
        ConversationActions.Request newRequest =
                new ConversationActions.Request.Builder(Arrays.asList(MSG_TWO))
                        .setExtras(extra)
                        .build();
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(newRequest, LANGUAGE_DETECTOR_EN)
                .get();
        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();

        assertThat(infos).hasSize(1);
        LanguageSignalInfo info = infos.get(0);
        assertThat(info).isEqualTo(US_INFO_ONE_FOR_CONVERSATION_ACTION_TWO);
    }

    @Test
    public void updateFromConversationActionsAsync_twoMessagesInDifferentLanguage()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(
                        CONVERSATION_ACTION_REQUEST_ONE, LANGUAGE_DETECTOR_EN)
                .get();
        ConversationActions.Request newRequest =
                new ConversationActions.Request.Builder(Arrays.asList(MSG_THREE)).build();
        mLanguageProfileUpdater
                .updateFromConversationActionsAsync(newRequest, LANGUAGE_DETECTOR_ZH)
                .get();
        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();

        assertThat(infos).hasSize(2);
        LanguageSignalInfo infoOne = infos.get(0);
        LanguageSignalInfo infoTwo = infos.get(1);
        assertThat(infoOne).isEqualTo(US_INFO_ONE_FOR_CONVERSATION_ACTION_ONE);
        assertThat(infoTwo)
                .isEqualTo(
                        new LanguageSignalInfo(
                                LANGUAGE_TAG_ZH,
                                LanguageSignalInfo.SUGGEST_CONVERSATION_ACTIONS,
                                1));
    }

    @Test
    public void updateFromClassifyTextAsync_classifyText()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromClassifyTextAsync(Collections.singletonList(LANGUAGE_TAG_EN))
                .get();
        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();

        assertThat(infos).hasSize(1);
        LanguageSignalInfo info = infos.get(0);
        assertThat(info)
                .isEqualTo(
                        new LanguageSignalInfo(
                                LANGUAGE_TAG_EN, LanguageSignalInfo.CLASSIFY_TEXT, 1));
    }

    @Test
    public void updateFromClassifyTextAsync_classifyTextTwice()
            throws ExecutionException, InterruptedException {
        mLanguageProfileUpdater
                .updateFromClassifyTextAsync(Collections.singletonList(LANGUAGE_TAG_EN))
                .get();
        mLanguageProfileUpdater
                .updateFromClassifyTextAsync(Collections.singletonList(LANGUAGE_TAG_ZH))
                .get();

        List<LanguageSignalInfo> infos = mDatabase.languageInfoDao().getAll();
        assertThat(infos).hasSize(2);
        LanguageSignalInfo infoOne = infos.get(0);
        LanguageSignalInfo infoTwo = infos.get(1);
        assertThat(infoOne)
                .isEqualTo(
                        new LanguageSignalInfo(
                                LANGUAGE_TAG_EN, LanguageSignalInfo.CLASSIFY_TEXT, 1));
        assertThat(infoTwo)
                .isEqualTo(
                        new LanguageSignalInfo(
                                LANGUAGE_TAG_ZH, LanguageSignalInfo.CLASSIFY_TEXT, 1));
    }
}