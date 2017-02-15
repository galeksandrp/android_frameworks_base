/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server;

import static com.android.internal.widget.LockPatternUtils.SYNTHETIC_PASSWORD_ENABLED_KEY;
import static com.android.internal.widget.LockPatternUtils.SYNTHETIC_PASSWORD_HANDLE_KEY;

import android.os.RemoteException;
import android.os.UserHandle;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.VerifyCredentialResponse;
import com.android.server.SyntheticPasswordManager.AuthenticationResult;
import com.android.server.SyntheticPasswordManager.AuthenticationToken;


/**
 * runtest frameworks-services -c com.android.server.SyntheticPasswordTests
 */
public class SyntheticPasswordTests extends BaseLockSettingsServiceTests {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testPasswordBasedSyntheticPassword() throws RemoteException {
        final int USER_ID = 10;
        final String PASSWORD = "user-password";
        final String BADPASSWORD = "bad-password";
        MockSyntheticPasswordManager manager = new MockSyntheticPasswordManager(mStorage, mGateKeeperService);
        AuthenticationToken authToken = manager.newSyntheticPasswordAndSid(mGateKeeperService, null,
                null, USER_ID);
        long handle = manager.createPasswordBasedSyntheticPassword(mGateKeeperService, PASSWORD,
                LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, authToken, USER_ID);

        AuthenticationResult result = manager.unwrapPasswordBasedSyntheticPassword(mGateKeeperService, handle, PASSWORD, USER_ID);
        assertEquals(result.authToken.deriveKeyStorePassword(), authToken.deriveKeyStorePassword());

        result = manager.unwrapPasswordBasedSyntheticPassword(mGateKeeperService, handle, BADPASSWORD, USER_ID);
        assertNull(result.authToken);
    }

    private void disableSyntheticPassword(int userId) throws RemoteException {
        mService.setLong(SYNTHETIC_PASSWORD_ENABLED_KEY, 0, UserHandle.USER_SYSTEM);
    }

    private void enableSyntheticPassword(int userId) throws RemoteException {
        mService.setLong(SYNTHETIC_PASSWORD_ENABLED_KEY, 1, UserHandle.USER_SYSTEM);
    }

    private boolean hasSyntheticPassword(int userId) throws RemoteException {
        return mService.getLong(SYNTHETIC_PASSWORD_HANDLE_KEY, 0, userId) != 0;
    }

    public void testPasswordMigration() throws RemoteException {
        final String PASSWORD = "testPasswordMigration-password";

        disableSyntheticPassword(PRIMARY_USER_ID);
        mService.setLockCredential(PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        final byte[] primaryStorageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);
        enableSyntheticPassword(PRIMARY_USER_ID);
        // Performs migration
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertEquals(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertTrue(hasSyntheticPassword(PRIMARY_USER_ID));

        // SP-based verification
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertArrayNotSame(primaryStorageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
    }

    private void initializeCredentialUnderSP(String password, int userId) throws RemoteException {
        enableSyntheticPassword(userId);
        mService.setLockCredential(password, password != null ? LockPatternUtils.CREDENTIAL_TYPE_PASSWORD : LockPatternUtils.CREDENTIAL_TYPE_NONE, null, userId);
    }

    public void testSyntheticPasswordChangeCredential() throws RemoteException {
        final String PASSWORD = "testSyntheticPasswordChangeCredential-password";
        final String NEWPASSWORD = "testSyntheticPasswordChangeCredential-newpassword";

        initializeCredentialUnderSP(PASSWORD, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        mService.setLockCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, PASSWORD, PRIMARY_USER_ID);
        mGateKeeperService.clearSecureUserId(PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertEquals(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
    }

    public void testSyntheticPasswordVerifyCredential() throws RemoteException {
        final String PASSWORD = "testSyntheticPasswordVerifyCredential-password";
        final String BADPASSWORD = "testSyntheticPasswordVerifyCredential-badpassword";

        initializeCredentialUnderSP(PASSWORD, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(PASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());

        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(BADPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
    }

    public void testSyntheticPasswordClearCredential() throws RemoteException {
        final String PASSWORD = "testSyntheticPasswordClearCredential-password";
        final String NEWPASSWORD = "testSyntheticPasswordClearCredential-newpassword";

        initializeCredentialUnderSP(PASSWORD, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        // clear password
        mService.setLockCredential(null, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, PASSWORD, PRIMARY_USER_ID);
        assertEquals(0 ,mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));

        // set a new password
        mService.setLockCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertNotSame(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
    }

    public void testSyntheticPasswordClearCredentialUntrusted() throws RemoteException {
        final String PASSWORD = "testSyntheticPasswordClearCredential-password";
        final String NEWPASSWORD = "testSyntheticPasswordClearCredential-newpassword";

        initializeCredentialUnderSP(PASSWORD, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        // clear password
        mService.setLockCredential(null, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        assertEquals(0 ,mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));

        // set a new password
        mService.setLockCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertNotSame(sid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
    }

    public void testSyntheticPasswordChangeCredentialUntrusted() throws RemoteException {
        final String PASSWORD = "testSyntheticPasswordClearCredential-password";
        final String NEWPASSWORD = "testSyntheticPasswordClearCredential-newpassword";

        initializeCredentialUnderSP(PASSWORD, PRIMARY_USER_ID);
        long sid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        // Untrusted change password
        mService.setLockCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        assertNotSame(0 ,mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertNotSame(sid ,mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));

        // Verify the password
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(NEWPASSWORD, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
    }


    public void testManagedProfileUnifiedChallengeMigration() throws RemoteException {
        final String UnifiedPassword = "testManagedProfileUnifiedChallengeMigration-pwd";
        disableSyntheticPassword(PRIMARY_USER_ID);
        disableSyntheticPassword(MANAGED_PROFILE_USER_ID);
        mService.setLockCredential(UnifiedPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        mService.setSeparateProfileChallengeEnabled(MANAGED_PROFILE_USER_ID, false, null);
        final long primarySid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        final long profileSid = mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID);
        final byte[] primaryStorageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);
        final byte[] profileStorageKey = mStorageManager.getUserUnlockToken(MANAGED_PROFILE_USER_ID);
        assertTrue(primarySid != 0);
        assertTrue(profileSid != 0);
        assertTrue(profileSid != primarySid);

        // do migration
        enableSyntheticPassword(PRIMARY_USER_ID);
        enableSyntheticPassword(MANAGED_PROFILE_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(UnifiedPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());

        // verify
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(UnifiedPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertEquals(primarySid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertArrayNotSame(primaryStorageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
        assertArrayNotSame(profileStorageKey, mStorageManager.getUserUnlockToken(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSyntheticPassword(PRIMARY_USER_ID));
        assertTrue(hasSyntheticPassword(MANAGED_PROFILE_USER_ID));
    }

    public void testManagedProfileSeparateChallengeMigration() throws RemoteException {
        final String primaryPassword = "testManagedProfileSeparateChallengeMigration-primary";
        final String profilePassword = "testManagedProfileSeparateChallengeMigration-profile";
        mService.setLockCredential(primaryPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, PRIMARY_USER_ID);
        mService.setLockCredential(profilePassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, null, MANAGED_PROFILE_USER_ID);
        final long primarySid = mGateKeeperService.getSecureUserId(PRIMARY_USER_ID);
        final long profileSid = mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID);
        final byte[] primaryStorageKey = mStorageManager.getUserUnlockToken(PRIMARY_USER_ID);
        final byte[] profileStorageKey = mStorageManager.getUserUnlockToken(MANAGED_PROFILE_USER_ID);
        assertTrue(primarySid != 0);
        assertTrue(profileSid != 0);
        assertTrue(profileSid != primarySid);

        // do migration
        enableSyntheticPassword(PRIMARY_USER_ID);
        enableSyntheticPassword(MANAGED_PROFILE_USER_ID);
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(primaryPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(profilePassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, MANAGED_PROFILE_USER_ID).getResponseCode());

        // verify
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(primaryPassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, PRIMARY_USER_ID).getResponseCode());
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(profilePassword, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, 0, MANAGED_PROFILE_USER_ID).getResponseCode());
        assertEquals(primarySid, mGateKeeperService.getSecureUserId(PRIMARY_USER_ID));
        assertEquals(profileSid, mGateKeeperService.getSecureUserId(MANAGED_PROFILE_USER_ID));
        assertArrayNotSame(primaryStorageKey, mStorageManager.getUserUnlockToken(PRIMARY_USER_ID));
        assertArrayNotSame(profileStorageKey, mStorageManager.getUserUnlockToken(MANAGED_PROFILE_USER_ID));
        assertTrue(hasSyntheticPassword(PRIMARY_USER_ID));
        assertTrue(hasSyntheticPassword(MANAGED_PROFILE_USER_ID));
    }
    // b/34600579
    //TODO: add non-migration work profile case, and unify/un-unify transition.
    //TODO: test token after user resets password
    //TODO: test token based reset after unified work challenge
    //TODO: test clear password after unified work challenge
}

