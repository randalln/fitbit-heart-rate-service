<h1>FB Heart Rate Monitor for Fitbit Sense, Versa 3, Versa 2, Versa Lite, Versa, and Ionic</h1>

<p>A pair of apps: one for a Fitbit&trade; smart watch, and one for an Android or iOS device, that
create a mock Bluetooth heart rate monitor.</p>

<p>This lets you share real time heart rate data from the Fitbit&trade; watch with exercise equipment (like Peloton) or fitness apps (like Strava, Zwift, or Wahoo).</p>

<p>Currently, the Fitbit&trade; smart watches measure heart rate, but do not advertise as generic
heart rate monitors and can only maintain one Bluetooth Low Energy connection with the
Fitbit&trade; mobile app.
As a workaround, we'll send heart rate data from the watch to the mobile device. Then, we'll run
our app that appears as a Bluetooth Low Energy peripheral and advertises as a generic heart
rate monitor. Finally, we'll push heart rate data from the Fitbit&trade; mobile app to our <strong>FB Heart Rate Monitor</strong> app.</p>

<p>Much thanks to the original author, <a href="https://github.com/mogenson/fitbit-heart-rate-service">@mogenson</a></p>

<p>Thanks to <a href="https://github.com/cwcpers/versa2-heart-rate-service?tab=readme-ov-file">@cwcpers</a>,
who got the watch app working on OS 4 devices (Versa 2, Versa Lite, Versa, Ionic)</p>

<h2>Requirements</h2>

<ol>
<li>One of the Fitbit&trade; smart watches (with the Fitbit Gallery) noted above and a device
   running at least Android 9 or iOS 15.6</li>
<li>If you plan on running a fitness app on a mobile device, e.g. Strava, you'll need a second
   device, because <strong>FB Heart Rate Monitor</strong> will need to run on the same device as the
   Fitbit&trade; app.<br>
   For example: I run Wahoo or Strava on my iPad or spare old Android phone and <strong>FB Heart
   Rate Monitor</strong> on my regular phone (where Fitbit&trade; is installed)</li>
</ol>

<h2>Installation</h2>

<ol>
<li>Install the <strong>HR Service</strong> Fitbit&trade; watch app from the Fitbit Gallery
  <ul>
    <li><a href="https://gallery.fitbit.com/details/6503a799-37f7-43bf-8775-23f1742e2a4e">Sense, Versa 3</a></li>
    <li><a href="https://gallery.fitbit.com/details/799d08f9-77d4-4a73-81dc-b187159a7124?key=c05ef0f4-c8f7-4459-94f7-50a209108fc6">Versa 2, Versa Lite, Versa, Ionic</a></li>
  </ul>
</li>
<li>Install the <strong>FB Heart Rate Monitor</strong> app:
  <h4>Android</h4>
  <a href="https://play.google.com/store/apps/details?id=org.noblecow.hrservice"><img alt="Install from Google Play" src="GetItOnGooglePlay_Badge_Web_color_English.png"></a>
  <h4>iOS</h4>
  <ol>
    <li>Install <a href="https://faq.altstore.io/">AltStore Classic</a></li>
    <li>Add a new source to AltStore Classic: https://randalln.github.io/fitbit-heart-rate-service/altstore.json</li>
    <li>Install <strong>FB Heart Rate Monitor</strong></li>
  </ol>
</li>
</ol>

<h2>Usage</h2>

<ol>
<li>Start the <strong>HR Service</strong> app on the Fitbit&trade; watch.</li>
<li>Start the <strong>FB Heart Rate Monitor</strong> app on the mobile device.
  <ul>
    <li>You should see the BPM value update with each new received value.</li>
  </ul>
</li>
<li>On your exercise equipment or an app like Strava (on your second mobile device), search for a
   Bluetooth heart rate monitor.
  <ul>
    <li>The mock heart rate monitor will have the same name as the mobile device.</li>
  </ul>
</li>
</ol>

<h3>Disclaimer</h3>

<p>No implied warranty or guarantee of functionality. The names Fitbit, Android, iOS, and Bluetooth
are trademarks of their respective owners.</p>

<p><a href="privacy-android.md">Privacy Policy</a></p>