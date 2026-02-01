# 🔍 Verify Firebase Setup - Step by Step

## ⚠️ Still Getting Permission Denied?

The error shows rules are still blocking writes. Let's verify everything step by step.

## Step 1: Verify Firebase Project

### Check google-services.json
1. Open `app/google-services.json`
2. Verify it exists and has content
3. Check the `project_id` matches your Firebase project

### Verify Package Name
1. Check `app/build.gradle.kts` → `applicationId`
2. Go to Firebase Console → Project Settings → Your apps
3. Verify package name matches

## Step 2: Deploy Rules (CRITICAL)

### Option A: Use Firebase Console (Recommended)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to **Realtime Database** (not Firestore!)
4. Click **Rules** tab
5. **DELETE ALL** existing rules
6. **PASTE** this (completely open for testing):
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
7. Click **Publish** (very important!)
8. Wait 10-20 seconds for rules to propagate

### Option B: Use Firebase CLI

If you have Firebase CLI installed:
```bash
firebase deploy --only database
```

## Step 3: Verify Rules Are Deployed

1. Go to Firebase Console → Realtime Database → Rules
2. You should see:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
3. If you see different rules, they weren't deployed correctly

## Step 4: Clear App Cache

Rules might be cached. Try:
1. Uninstall the app completely
2. Reinstall the app
3. Login again
4. Try creating a report

## Step 5: Check Database Region

1. Go to Firebase Console → Realtime Database
2. Check the database URL
3. It should be something like: `https://[project-id]-default-rtdb.firebaseio.com/`
4. If you have multiple databases, make sure you're using the correct one

## Step 6: Verify Realtime Database is Enabled

1. Go to Firebase Console → Realtime Database
2. If you see "Get started", click it to enable
3. Choose a location (us-central1, etc.)
4. Wait for database to be created

## Step 7: Test Connection in App

The app now has a connection test. When you create a report, check Logcat for:

```
=== TESTING FIREBASE CONNECTION ===
Auth UID: [uid]
Database URL: [url]
✓ TEST PASSED: Can write to database
```

OR

```
✗ TEST FAILED: Cannot write to database
Error: Permission denied
```

## Step 8: Check Authentication

1. Make sure user is logged in via Firebase Auth
2. Check Logcat for: "Current authenticated user: [uid]"
3. If you see "No authenticated user", user needs to login

## Step 9: Alternative - Use Firestore Instead

If Realtime Database continues to fail, we can switch to Firestore which has different rules. But first, let's fix Realtime Database.

## Common Issues

### Issue 1: Rules Not Published
**Symptom:** Rules look correct in console but still get permission denied
**Solution:** 
- Make sure you clicked **Publish** (not just Save)
- Wait 20-30 seconds
- Clear app cache and reinstall

### Issue 2: Wrong Database
**Symptom:** Rules deployed but still failing
**Solution:**
- Check if you have multiple databases
- Verify you're deploying rules to the correct database
- Check database URL in code matches Firebase Console

### Issue 3: Cached Rules
**Symptom:** Changed rules but still getting old errors
**Solution:**
- Uninstall app completely
- Clear Firebase cache (if possible)
- Reinstall and test

### Issue 4: Authentication Not Working
**Symptom:** User logged in but Firebase Auth shows null
**Solution:**
- User must login via Firebase Auth (not just SharedPreferences)
- Check `FirebaseAuth.getInstance().getCurrentUser()` is not null
- Re-login if needed

## Debug Checklist

- [ ] `google-services.json` exists and is correct
- [ ] Package name matches Firebase project
- [ ] Realtime Database is enabled in Firebase Console
- [ ] Rules are deployed (check Firebase Console)
- [ ] Rules are completely open: `.read: true, .write: true`
- [ ] Clicked "Publish" after updating rules
- [ ] Waited 20-30 seconds after publishing
- [ ] User is authenticated (check Logcat)
- [ ] App cache cleared / app reinstalled
- [ ] Database URL matches Firebase Console

## Next Steps

1. **Deploy the open rules** (Step 2)
2. **Wait 30 seconds** after publishing
3. **Uninstall and reinstall** the app
4. **Login again**
5. **Create a report**
6. **Check Logcat** for connection test results

If connection test passes but report save fails, there's a different issue.
If connection test fails, the rules are definitely not deployed correctly.



