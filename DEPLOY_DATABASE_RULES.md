# ⚠️ CRITICAL: Deploy Database Rules to Fix Permission Denied Error

## Error
```
Permission denied at /reports/{reportId}
DatabaseError: Permission denied
```

## Solution

### Step 1: Copy the Updated Rules

The `database.rules.json` file has been updated with the correct rules. Copy the entire content:

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid || (auth.uid !== null && root.child('users').child(auth.uid).child('role').val() === 'admin')",
        ".write": "$userId === auth.uid || (auth.uid !== null && root.child('users').child(auth.uid).child('role').val() === 'admin')"
      }
    },
    "reports": {
      "$reportId": {
        ".read": "auth != null && (data.child('patientId').val() === auth.uid || data.child('doctorId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin' || root.child('users').child(auth.uid).child('role').val() === 'doctor')",
        ".write": "auth != null && (!data.exists() ? (newData.child('patientId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin') : (data.child('patientId').val() === auth.uid || data.child('doctorId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin' || root.child('users').child(auth.uid).child('role').val() === 'doctor'))"
      }
    },
    "submissions": {
      "$submissionId": {
        ".read": "auth != null && (data.child('userId').val() === auth.uid || data.child('assignedDoctorId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin')",
        ".write": "auth != null && (!data.exists() ? (newData.child('userId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin') : (data.child('userId").val() === auth.uid || data.child('assignedDoctorId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'admin'))"
      }
    }
  }
}
```

### Step 2: Deploy to Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to **Realtime Database** → **Rules** tab
4. **DELETE** all existing rules
5. **PASTE** the new rules from above
6. Click **Publish**

### Step 3: Verify Rules

After publishing, the rules should allow:
- ✅ Authenticated users to create reports where `patientId === auth.uid`
- ✅ Users to update their own reports
- ✅ Doctors to update assigned reports
- ✅ Admins to update all reports

### Step 4: Test

1. Make sure user is logged in via Firebase Auth
2. Create a report in the app
3. Check Logcat for: "✓ Report successfully saved to Realtime Database"
4. Check Firebase Console → Realtime Database → reports
5. Verify the report appears

## Code Changes Made

1. **Added authentication check** - Reports can only be saved if user is authenticated
2. **Auto-fix patientId** - Ensures `patientId` matches `auth.uid` before saving
3. **Better error logging** - Shows exactly why save failed

## Important Notes

- **User MUST be logged in** via Firebase Auth before saving reports
- **patientId MUST match auth.uid** (code now auto-fixes this)
- **Database rules MUST be deployed** to Firebase Console

If you still get permission denied after deploying rules:
1. Check if user is authenticated (Logcat will show "No authenticated user")
2. Check if patientId matches auth.uid (Logcat will show both values)
3. Verify rules were published successfully in Firebase Console



