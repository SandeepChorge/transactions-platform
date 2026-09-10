---
layout: default
title: Delete account and data — Statement Sense
---

# Delete your Statement Sense account and data

**App:** Statement Sense / Transactions Parser  
**Developer:** Sandeep Chorge  
**Package:** `com.madtitan94.transactionsparser`  
**Last updated:** 10 September 2026

Statement Sense uses Google sign-in and stores your profile and parsed financial records in the
app's private storage on your device. We do not operate an account or financial-data backend.
The app also uses Google's Firebase Analytics and Crashlytics, and Android system backups may
contain app data. These are separate from the app's local storage.

## Delete from the app

1. Open Statement Sense and select the **You** tab to open **Settings**.
2. Scroll to the bottom and tap **Delete account**.
3. Read the confirmation and tap **Delete account** again.
4. After deletion completes, the app returns to the Google login screen.

This signs you out of Statement Sense and permanently clears **all app-managed local data for
every account used on that device**: transactions, statement history, payee mappings and identifiers,
categories (including recently deleted items), upload logs, profile details, dashboard preferences,
appearance settings, and cached files. Local Firebase data is reset through the SDKs. There is no
local recovery period. If deletion fails, some data may already have been removed; retry the action.

This does not delete your Google account, original PDFs, files you exported, Android backups,
or data on another device. You can sign in again to start with an empty local account.

## If you cannot use the app

Account and local-data deletion must be performed on your device. We do not keep a central
account or financial-data database, and support cannot access or erase the app's private storage
remotely. Sending an email does not delete your local account or data.

If the app is still installed but you cannot open it, use Android **Settings → Apps → Statement
Sense → Storage & cache → Clear storage** (sometimes called **Clear data**), or uninstall the app.
**Clear cache** alone does not remove your account or database.

If you already uninstalled the app, Android has removed its private local data; there is no need
to reinstall it solely to erase that local copy. Original PDFs, exported files, backups and data
on other devices remain separate. Manage Android backups so an older copy is not restored on
reinstall.

## What is deleted and what may remain

| Data | What happens |
|---|---|
| Local session and Google sign-in state | Cleared when in-app deletion succeeds; your Google account remains active |
| Parsed transactions, mappings, categories, statement history, and upload logs | Permanently removed from the local database, including all accounts and recently deleted records |
| Profile, dashboard and appearance preferences, cached files | Cleared from the app's local stores |
| Original PDFs, exported CSVs and JSON backups | Remain wherever you saved them; delete them using the relevant Files or cloud-storage app |
| Android system backups and copies on other devices | Not erased by local deletion; manage them through Android/Google backup settings and each device |
| Already uploaded Firebase analytics and crash reports | Not erased by the in-app local reset. These remain subject to the retention periods described in our [Privacy Policy](privacy-policy) |
| Google account and authorization | Your Google account is not deleted. You can also remove Statement Sense's connection in [Google account permissions](https://myaccount.google.com/permissions) |

Signing out alone preserves local financial records and preferences. Use **Delete account** when
you want the complete local reset described above.

For more details, read our [Privacy Policy](privacy-policy). For help with these steps, contact
[statementsensesupport@gmail.com](mailto:statementsensesupport@gmail.com); support cannot perform
local deletion on your behalf.
