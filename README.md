# Junction Plus – Android app

An Android app for **stream.junctionplus.com**. Subscribers sign in and watch
just as they do on the website. Uscreen handles sign-in, payments and the free
preview, so anything you change in Uscreen shows up in the app straight away.
New videos don't need an app update.

## Download
Direct link to the newest version (good for a Downloader code):

https://github.com/use2025me/junction-plus-app/releases/latest/download/JunctionPlus.apk

This link always points at the latest build.

## What the app does
- Opens stream.junctionplus.com full screen, with the Junction Plus icon and splash screen
- Keeps subscribers signed in between visits
- Full-screen video turns to landscape and hides the phone's bars
- The Back button goes back a page (or exits full-screen video)
- Junction Plus, Uscreen and checkout pages stay in the app; other links open in the browser
- Shows a "No connection" screen with a Try again button when offline
- **Update check:** when the app opens, it checks GitHub for a newer version and opens the download link (on TVs it shows the Downloader code). The app never installs anything itself.

## Fire TV and Android TV
- Install with the Downloader app using code **3530210**
- The app appears on the TV home screen with the Junction Plus logo tile
- **Arrow buttons** move the on-screen pointer; it scrolls the page at the top and bottom edges
- **OK** clicks whatever the pointer is on
- **Back** goes back a page, or leaves full-screen video
- In full-screen video, **OK** pauses or plays, and **left/right** skip back or forward 10 seconds
- **Play/Pause, Rewind and Fast-forward** buttons on the remote control the video

## How updates work
Every change pushed to the `main` branch starts the **Build Junction Plus app**
workflow (Actions tab). It:
1. builds the app and signs it with the Junction Plus key (stored as repository secrets),
2. gives it a higher version number,
3. publishes it as a new release with `JunctionPlus.apk` attached.

Installed apps see the new release next time they open, and the update
installs over the old version because the signing key never changes.

## Signing key – keep it safe
The key is stored encrypted in `signing/release.jks.enc` and can only be
unlocked with the repository secret `SIGNING_PASSWORD` (key alias `junctionplus`).
Keep your own backup copy of the key file and password somewhere safe.
Never put the password itself in this repository. If the key is lost, people
will have to uninstall the app before a new one will install, and you'll need
the same key for Google Play later.

## Changing things
- Start page or which websites stay in the app: `app/src/main/java/com/junctionplus/app/Config.kt`
- App name: `app/src/main/res/values/strings.xml`
- Colours: `app/src/main/res/values/colors.xml`
- Icon: `app/src/main/res/drawable/ic_launcher_foreground.xml`

## Before Google Play
- Google Play may reject apps that are mainly a website, and its payments policy
  may apply to subscriptions sold through the site. Check the current policies first.
- Apps from Google Play can't use the built-in update check. Google Play handles
  updates itself, so remove it for that version.
- "Sign in with Google" doesn't work inside this kind of app. Email/password sign-in works.
