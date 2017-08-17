Android FIDO U2F API Sample
===========================

A basic app showing how to register and authenticate FIDO U2F
Security Keys using the FIDO U2F API.

FIDO U2F API is used for devices running Android L (API level 21)
or newer.

Introduction
------------
The Fast Identity Online Universal 2nd Factor (FIDO U2F) API is now available.
It provides U2F physical security key support to apps, in accordance with the standards defined by
the FIDO Alliance.

This API should be accessed from U2fApiClient entry point. For example, here is how to get an
instance of it:
```java
private U2fApiClient mU2fApiClient;
…
mU2fApiClient = Fido.getU2fApiClient(this /* calling activity */);

```

U2fPendingIntent can be used to launch U2F request. Here are different ways for registration and
sign respectively to get a U2fPendingIntent from the entry point.
For a registration request:
```java
Task<U2fPendingIntent> result =
     mU2fApiClient.getRegisterIntent(registerRequestParams);

```

For a sign request:
```java
Task<U2fPendingIntent> result =
     mU2fApiClient.getSignIntent(signRequestParams);

```

launchPendingintent(Activity, int) can be used to launch the U2F request.
```java
result.addOnSuccessListener(
     new OnSuccessListener<U2fPendingIntent>() {
       @Override
       public void onSuccess(U2fPendingIntent u2fPendingIntent) {
         if (u2fPendingIntent.hasPendingIntent()) {
           // Start a U2F registration request.
           u2fPendingIntent.launchPendingIntent(this, REGISTER_REQUEST_CODE);
           // For a U2F sign request.
           // u2fPendingIntent.launchPendingIntent(this, SIGN_REQUEST_CODE);
         }
       }
     });

 result.addOnFailureListener(
     new OnFailureListener() {
       @Override
       public void onFailure(Exception e) {
           // fail
       }
     });

```


Handling the result:
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  if (resultCode != RESULT_OK) {
    // Something went wrong
  }

  switch(requestCode) {
    case REGISTER_REQUEST_CODE:
      RegisterResponseData registerResponse = (RegisterResponseData) data
          .getParcelableExtra(Fido.KEY_RESPONSE_EXTRA);
      // Do something useful
      break;
    case SIGN_REQUEST_CODE:
      SignResponseData signResponse = (SignResponseData) data
          .getParcelableExtra(Fido.KEY_RESPONSE_EXTRA);
      // Do something useful
      break;
    default:
      // Something went wrong
  }
}
```


Pre-requisites
--------------

- Android SDK 26
- Android Build Tools v25.0.3
- Android Support Repository


Getting Started
---------------

To install the sample app on your Android device or emulator,
run `./gradlew :app:installRelease`. This will install the release
configuration, which uses the bundled keystore file to make the app
work with the demo server.

Support
-------

- Google+ Community: https://plus.google.com/communities/105153134372062985968
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/android-fido

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.


License
-------

Copyright 2017 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
