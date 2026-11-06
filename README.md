## ⚠️ Important Limitations & Requirements

Because this app relies entirely on reading local text messages instead of connecting to an internet cloud API, there are two main limitations you should know about before using it:

* **Android Only:** Apple's iOS strictly blocks third-party apps from reading your SMS messages. Since this app's entire engine requires scanning your local inbox to work offline, it is only available for Android devices.
* **Personal SIM Cards Only:** This app is built to read standard Safaricom M-Pesa receipts sent to personal phone numbers (like "Confirmed. Ksh...", or "You have received..."). It will not work for Business Till numbers, merchant Paybills, or agent lines, as those use completely different SMS formats and require Safaricom's Daraja API.


M-Pesa Tracker

Welcome to my first major Android project!

I built M-Pesa Tracker because I wanted a simple, private way to track my expenses without uploading my personal financial data to the cloud. I am really enthusiastic about Android development, and building this from has been an incredible way for me to learn how to put a real app together.

M-Pesa Tracker is an offline expense manager. It reads your Safaricom M-Pesa SMS receipts directly on your device, figures out how much you spent, tracks your budgets, and keeps everything stored locally.

you can search and filter according to any information given

add notes to specific transactions and categorise them

ability for you to paste mpesa messages where the app will extract the needed infomation if you dont like the idea of giving the app permision to accesss you messages

How it works

SMS Reading: The app listens for texts specifically from "MPESA".

Data Extraction: It pulls out the transaction type (like Send Money or Paybill), the amount in Ksh, the transaction cost, and the recipient's name.

Local Storage: All this data is saved into a local database on your phone. Your data never leaves your device and requires no internet connection.

Live Updates: The dashboard watches the database and automatically updates your balances and budget progress.

Tech Stack

I used modern Android tools to build this:

Kotlin

Jetpack Compose for the UI

MVVM Architecture

Dagger Hilt for dependency injection

Room Database for local storage

Kotlin Coroutines and Flow

How to install it on your phone

Because this app reads SMS messages, the best way to use it right now is to sideload it directly onto your phone.

Get the app: Go to the Releases tab on this repository and download the app-debug.apk file. (Alternatively, you can clone this repo and build it yourself using ./gradlew assembleDebug).

Install it: Transfer the APK to your Android phone and tap it to install. You may need to go into your settings to allow installation from unknown sources.

Allow permissions: When you first open the app, it will ask for SMS and Notification permissions. You must allow these so the app can read your M-Pesa texts and warn you if you go over budget.

Sync your data: Go to the home screen and press the sync button. It will scan your current inbox for old M-Pesa messages and instantly build your dashboard.

What's next

This is just the first version. As I keep learning, I want to add things like custom categories for specific shops, fingerprint lock for security, and exporting reports to Excel.

Thanks for checking out my first project! Feel free to look through the code or suggest improvements.

Asante Sana.