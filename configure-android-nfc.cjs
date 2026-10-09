const fs = require('node:fs');
const path = require('node:path');
function configure(root) {
  const android = path.join(root, 'android/app');
  const manifest = path.join(android, 'src/main/AndroidManifest.xml');
  if (!fs.existsSync(manifest)) throw Error('Créez la plateforme avec npx cap add android avant cette étape.');
  const config = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8'));
  const javaDir = path.join(android, 'src/main/java', ...config.appId.split('.'));
  fs.mkdirSync(javaDir, { recursive: true });
  const activity = `package ${config.appId};\n\nimport android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;\nimport com.chemicalstockmanager.nfc.NfcPlugin;\n\npublic class MainActivity extends BridgeActivity {\n    @Override public void onCreate(Bundle savedInstanceState) {\n        registerPlugin(NfcPlugin.class);\n        super.onCreate(savedInstanceState);\n    }\n}\n`;
  fs.writeFileSync(path.join(javaDir, 'MainActivity.java'), activity);
  const pluginDir = path.join(android, 'src/main/java/com/chemicalstockmanager/nfc');
  fs.mkdirSync(pluginDir, { recursive: true });
  fs.copyFileSync(path.join(root, 'native-android/NfcPlugin.java'), path.join(pluginDir, 'NfcPlugin.java'));
  const testDir = path.join(android, 'src/test/java/com/chemicalstockmanager/nfc');
  fs.mkdirSync(testDir, { recursive: true });
  fs.copyFileSync(path.join(root, 'native-android/NfcPluginTest.java'), path.join(testDir, 'NfcPluginTest.java'));
  let xml = fs.readFileSync(manifest, 'utf8');
  const additions = [
    ['android.permission.CAMERA', '<uses-permission android:name="android.permission.CAMERA"/>'],
    ['android.permission.NFC', '<uses-permission android:name="android.permission.NFC"/>'],
    ['android.hardware.camera', '<uses-feature android:name="android.hardware.camera" android:required="false"/>'],
    ['android.hardware.nfc', '<uses-feature android:name="android.hardware.nfc" android:required="false"/>'],
    ['android.permission.WRITE_EXTERNAL_STORAGE', '<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>'],
    ['android.permission.READ_EXTERNAL_STORAGE', '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>']
  ];
  for (const [key, entry] of additions) if (!xml.includes(`android:name="${key}"`)) xml = xml.replace('<application', `${entry}\n    <application`);
  fs.writeFileSync(manifest, xml);
  const gradle = path.join(android, 'build.gradle');
  let build = fs.readFileSync(gradle, 'utf8');
  build = build.replace(/versionCode\s+\d+/, 'versionCode 34').replace(/versionName\s+"[^"]+"/, 'versionName "3.4.0"');
  if (!build.includes('unitTests.includeAndroidResources')) build = build.replace('android {', 'android {\n    testOptions { unitTests.includeAndroidResources = true }');
  if (!build.includes("org.robolectric:robolectric:")) build = build.replace('dependencies {', "dependencies {\n    testImplementation 'org.robolectric:robolectric:4.14.1'");
  fs.writeFileSync(gradle, build);
  return {appId: config.appId, manifest, javaDir};
}
if (require.main === module) console.log('Module NFC intégré :', configure(path.resolve(__dirname, '..')).appId);
module.exports = { configure };
