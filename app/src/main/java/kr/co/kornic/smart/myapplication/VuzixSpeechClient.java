/*
 * Copyright (c) 2016, Vuzix Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * *  Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * *  Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * *  Neither the name of Vuzix Corporation nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package kr.co.kornic.smart.myapplication;

import android.app.Fragment;
import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ContentUris;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * <h1>Vuzix Speech Client local vocabulary configurator for Smart Glasses Speech Recognition Service</h1>
 *
 * A <b>Vocabulary</b> is a set of phrases recognized by the Vuzix Speech Recognition
 * Service
 *
 * @author Scott Wagner <scott_wagner@vuzix.com>
 * @version 1.0
 * @since 2017-09-13
 */

public class VuzixSpeechClient {
    private final static String TAG = VuzixSpeechClient.class.getSimpleName();
    private final int mClientId;
    private final String mClientIdStr;
    private final String mPackageName;
    private final ContentResolver mResolver;
    private final Boolean mIsActivity;
    private HashMap<String, Pair<Integer, String>> mVocabularyMap; // Phrase => Pair<Index, Params>
    private HashMap<String, String> mIntentMap; // Label => Intent (as string)

    public final static String ACTION_VOICE_COMMAND = "com.vuzix.action.VOICE_COMMAND";
    private static final class SpeechRecognitionContract {
        public static final String AUTHORITY = "com.vuzix.speechrecognitionservice.SpeechRecognitionVocabulary";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
        public final String SELECTION_ID_BASED = BaseColumns._ID + " = ? ";
        public interface VocabularyColumns extends BaseColumns {
            public final String PHRASES = "phrases";
            public final String PHRASE_ID = "phraseId";
            public final String LABELS = "labels";
            public final String LABEL_ID = "labelId";
            public final String PARAMS = "params";
            public final String INTENTS = "intents";
            public final String ACTIVITY = "activity";
            public final String ERROR = "error";
        }
        public static final class Phrases implements BaseColumns {
            public final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                    '/' + AUTHORITY + '.' + VocabularyColumns.PHRASES;
            public final String ENTRY_CONTENT_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
                    '/' + AUTHORITY + '.' + VocabularyColumns.PHRASES;
            public static final Uri CONTENT_URI = Uri.withAppendedPath(SpeechRecognitionContract.CONTENT_URI, VocabularyColumns.PHRASES);
        }
        public static final class Intents implements BaseColumns {
            public final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                    '/' + AUTHORITY + '.' + VocabularyColumns.LABELS;
            public final String ENTRY_CONTENT_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
                    '/' + AUTHORITY + '.' + VocabularyColumns.LABELS;
            public static final Uri CONTENT_URI = Uri.withAppendedPath(SpeechRecognitionContract.CONTENT_URI, VocabularyColumns.LABELS);
        }
    }

    /**
     * Create a {@link VuzixSpeechClient} object for creation of a local
     * <b>Vocabulary</b> with the scope of an {@link android.app.Activity}.  This
     * class should be used within {@link android.app.Activity#onCreate} to
     * register a <b>Vocabulary</b> which will be used whenever this Activity
     * has focus (is Resumed).
     *
     * @param activity   The {@link android.app.Activity} for which the <b>Vocabulary</b> is in scope
     *
     */
    public VuzixSpeechClient(Activity activity) throws RemoteException {
        mClientId = activity.hashCode();
        mClientIdStr = Integer.toString(mClientId);
        mPackageName = activity.getPackageName();
        mResolver = activity.getContentResolver();
        mIsActivity = true;
        LoadVocabulary();
    }


    public VuzixSpeechClient(Fragment fragment) throws RemoteException {
        mClientId = fragment.hashCode();
        mClientIdStr = Integer.toString(mClientId);
        mPackageName = fragment.getActivity().getPackageName();
        mResolver = fragment.getActivity().getContentResolver();
        mIsActivity = false;
        LoadVocabulary();
    }

    private void LoadVocabulary() throws RemoteException {
        String[] selectionArgs = new String[] { mPackageName };
        Cursor cursor = mResolver.query(SpeechRecognitionContract.Phrases.CONTENT_URI, null, mClientIdStr, selectionArgs, null);
        if (cursor == null) {
            throw new RemoteException("SpeechRecognitionService content resolver failure");
        }
        int nameColumn = cursor.getColumnIndex(SpeechRecognitionContract.VocabularyColumns.PHRASES);
        int indexColumn = cursor.getColumnIndex(SpeechRecognitionContract.VocabularyColumns._ID);
        int phraseCount = cursor.getCount();
        mVocabularyMap = new HashMap<String, Pair<Integer, String>>(phraseCount + 16); // Arbitrarily size for 16 more than needed
        while (cursor.moveToNext()) {
            // We will populate phrase params individually as needed
            mVocabularyMap.put(cursor.getString(nameColumn), new Pair<Integer, String>(new Integer(cursor.getInt(indexColumn)), null));
        }
        cursor.close();
        mIntentMap = null; // We will populate the intent map if we need it
    }

    public String dump() {
        String s = new String("Vocabulary for " + mPackageName + " ID " + mClientIdStr + ':');
        Pair<Integer, String> p;
        for (String key : mVocabularyMap.keySet()) {
            p = mVocabularyMap.get(key);
            s += "\n\"" + key + "\" : " + p.first + ", \"" + p.second + '"';
        }
        return s;
    }

    public boolean insertPhrase(String phrase) {
        return insertPhrase(phrase, null);
    }

    public boolean insertPhrase(String phrase, String substitution) {
        Pair<Integer, String> p = mVocabularyMap.get(phrase);
        int index;
        if (substitution != null && substitution.indexOf(';') < 0 &&
                !substitution.startsWith("s:") && !substitution.startsWith("S:")) {
            substitution = new String("s:" + substitution);
        }
        ContentValues cv = new ContentValues();
        cv.put(SpeechRecognitionContract.VocabularyColumns.ACTIVITY, mPackageName);
        cv.put(SpeechRecognitionContract.VocabularyColumns.PHRASES, phrase);
        cv.put(SpeechRecognitionContract.VocabularyColumns.PARAMS, substitution);
        if (p == null) {
            cv.put(SpeechRecognitionContract.VocabularyColumns._ID, mClientId);
            Uri result = mResolver.insert(SpeechRecognitionContract.Phrases.CONTENT_URI, cv);
            index = (int)ContentUris.parseId(result);
        } else {
            index = p.first.intValue();
            mResolver.update(ContentUris.withAppendedId(SpeechRecognitionContract.Phrases.CONTENT_URI, index),
                    cv, mClientIdStr, null);
        }
        if (substitution == null) {
            mVocabularyMap.put(phrase, new Pair<Integer, String>(new Integer(index), ""));
        } else {
            mVocabularyMap.put(phrase, new Pair<Integer, String>(new Integer(index), substitution));
        }
        return true;
    }

    public boolean insertKeycodePhrase(String phrase, int keyevent, int repeatIntervalMs, String params) {
        String substitution = new String("s:&k" + keyevent);
        if (repeatIntervalMs > 0) {
            substitution += ";r:" + repeatIntervalMs;
        }
        if (params != null && params.length() > 0) {
            substitution += ";" + params;
        }
        return insertPhrase(phrase, substitution);
    }

    public boolean insertKeycodePhrase(String phrase, int keyevent, String params) {
        return insertKeycodePhrase(phrase, keyevent, 0, params);
    }

    public boolean insertKeycodePhrase(String phrase, int keyevent, int repeatIntervalMs) {
        return insertKeycodePhrase(phrase, keyevent, repeatIntervalMs, null);
    }

    public boolean insertKeycodePhrase(String phrase, int keyevent) {
        return insertKeycodePhrase(phrase, keyevent, 0, null);
    }

    public boolean insertIntentPhrase(String phrase, String intentLabel, String params) throws RemoteException {
        populateIntentMap();
        if (!mIntentMap.containsKey(intentLabel)) {
            Log.e(TAG, "Intent map label " + intentLabel + " not defined - cannot insert phrase");
            return false;
        }
        String substitution = new String("s:&i" + intentLabel);
        if (params != null && params.length() > 0) {
            substitution += ";" + params;
        }
        return insertPhrase(phrase, substitution);
    }

    public boolean insertIntentPhrase(String phrase, String intentLabel) throws RemoteException {
        return insertIntentPhrase(phrase, intentLabel, null);
    }

    public boolean deletePhrase(String phrase) {
        Pair<Integer, String> p = mVocabularyMap.get(phrase);
        if (p == null) {
            return false;
        }
        int index = p.first.intValue();
        String[] selectionArgs = new String[] { mPackageName };
        mResolver.delete(ContentUris.withAppendedId(SpeechRecognitionContract.Phrases.CONTENT_URI, index),
                mClientIdStr, selectionArgs);
        mVocabularyMap.remove(phrase);
        // The removal of this entry in the SpeechRecognition vocabulary will decrement the index of everything above it.
        for (String key : mVocabularyMap.keySet()) {
            p = mVocabularyMap.get(key);
            if (p.first.intValue() > index) {
                mVocabularyMap.put(key, new Pair<Integer, String>(p.first.intValue() - 1, p.second));
            }
        }
        return true;
    }

    private void populateIntentMap() throws RemoteException {
        if (mIntentMap == null) {
            String[] selectionArgs = new String[] { mPackageName };
            Cursor cursor = mResolver.query(SpeechRecognitionContract.Intents.CONTENT_URI, null, mClientIdStr, selectionArgs, null);
            if (cursor == null) {
                throw new RemoteException("SpeechRecognitionService content resolver failure");
            }
            int labelColumn = cursor.getColumnIndex(SpeechRecognitionContract.VocabularyColumns.LABELS);
            int phraseCount = cursor.getCount();
            mIntentMap = new HashMap<String, String>(phraseCount + 16); // Arbitrarily size for 16 more than needed
            while (cursor.moveToNext()) {
                mIntentMap.put(cursor.getString(labelColumn), null);
                Log.i(TAG, "Found existing intent label " + cursor.getString(labelColumn));
            }
            cursor.close();
        }
    }

    public boolean defineIntent(String label, Intent intent) throws RemoteException {
        String val;
        populateIntentMap();
        if (mIntentMap.containsKey(label)) {
            Log.e(TAG, "Intent map label " + label + " already exists and may not be modified.");
            return false;
        }
        val = intent.getAction();
        if (val == null) {
            val = ACTION_VOICE_COMMAND;
        }
        String ret = new String("action:" + val);
        Uri u = intent.getData();
        if (u != null) {
            ret = ret.concat(";data:"+u.toString());
        }
        Set<String> categories = intent.getCategories();
        if (categories != null) {
            Iterator<String> iterator = categories.iterator();
            while(iterator.hasNext()) {
                ret = ret.concat(";category:" + iterator.next());
            }
        }
        val = intent.getType();
        if (val != null) {
            ret = ret.concat(";type:" + val);
        }
        ComponentName component = intent.getComponent();
        if (component != null) {
            ret = ret.concat(";component:" + component.flattenToString());
        }
        Bundle extras = intent.getExtras();
        if (extras != null) {
            Set<String> keys = extras.keySet();
            Iterator<String> iterator = keys.iterator();
            while(iterator.hasNext()) {
                String bkey = iterator.next();
                ret = ret.concat(";" + bkey + ":" + extras.get(bkey).toString());
            }
        }
        ContentValues cv = new ContentValues();
        cv.put(SpeechRecognitionContract.VocabularyColumns.ACTIVITY, mPackageName);
        cv.put(SpeechRecognitionContract.VocabularyColumns.LABELS, label);
        cv.put(SpeechRecognitionContract.VocabularyColumns.INTENTS, ret);
        cv.put(SpeechRecognitionContract.VocabularyColumns._ID, mClientId);
        Uri result = mResolver.insert(SpeechRecognitionContract.Intents.CONTENT_URI, cv);
        int index = (int)ContentUris.parseId(result);
        mIntentMap.put(label, ret);
        return (index >= 0);
    }
}


