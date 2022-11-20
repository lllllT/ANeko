ANeko
=====


[![version](https://encrypted-tbn0.gstatic.com/images?q=tbn%3AANd9GcQKUUtefY6l9YGHF0GmQAijrUAUueZcJqwGIA&usqp=CAU)](https://play.google.com/store/apps/details?id=org.renewal.aneko)


Introduction
------------
Android version of neko (or nekoDA, xneko, oneko, etc...)


Build
-----

Prerequisites:

* [Android Studio](https://developer.android.com/studio)

How to Build :
* File -> Open -> Select Project
* Sync Project with Gradle

Then clean, compile and run.


To create skin
--------------

Support both app-method and internal file method.  
  
See [skin document](http://www.tamanegi.org/prog/android-apps/aneko-skin.html#create-skin) for app method.  
See [how-to-make skin file(korean)](https://gall.dcinside.com/mgallery/board/view/?id=micateam&no=857787) for internal file method. 

### Changelogs from legecy ANeko

~ Sep 23, 2020
 - changed minSDK to 19 (Android 4.4)
 - Migrated Support library to AndroidX
 - Changed PreferenceActivity to PreferenceFragmentCompat and AppCompatActivity
 - fixed ACTION_MANAGE_OVERLAY_PERMISSION problem on SDK 23 (Android 6.0) or more
 - Added WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE on manifest
 - Deleted SkinPreference.java
 - Changed to be able to import skin files from internal storage
 - Migrated Eclipse ADT project to Intelij IDEA Gradle style project
 - Deleted behaviour option and added size option
 - Optimize performance
 
~ Aug 28, 2022
 - Changed target SDK to 33 (Android 13)
 - Applied Material You style
 - Dropped load skin from local storage option
 - Dropped support of Android 19 (4.4) and under
 
