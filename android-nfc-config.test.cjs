const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),vm=require('node:vm');
const {configure}=require('../scripts/configure-android-nfc.cjs');
const root=path.resolve(__dirname,'..');
test('Configuration Android NFC: permission, module, Activity et tests; réexécution sans doublons',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'chem-nfc-config-'));
 try{
  fs.mkdirSync(path.join(dir,'android/app/src/main'),{recursive:true});fs.mkdirSync(path.join(dir,'native-android'));
  fs.writeFileSync(path.join(dir,'capacitor.config.json'),JSON.stringify({appId:'com.chemicalstockmanager.standalone'}));
  fs.writeFileSync(path.join(dir,'android/app/src/main/AndroidManifest.xml'),'<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".MainActivity"/></application></manifest>');
  fs.writeFileSync(path.join(dir,'android/app/build.gradle'),'android { defaultConfig { versionCode 1; versionName "1.0" } }\ndependencies { }');
  for(const file of ['NfcPlugin.java','NfcPluginTest.java'])fs.copyFileSync(path.join(root,'native-android',file),path.join(dir,'native-android',file));
  configure(dir);configure(dir);
  const xml=fs.readFileSync(path.join(dir,'android/app/src/main/AndroidManifest.xml'),'utf8');
  assert.equal((xml.match(/android.permission.NFC/g)||[]).length,1);assert.match(xml,/android.hardware.nfc" android:required="false"/);assert(xml.includes('<activity android:name=".MainActivity"/>'));
  const activity=fs.readFileSync(path.join(dir,'android/app/src/main/java/com/chemicalstockmanager/standalone/MainActivity.java'),'utf8');assert(activity.indexOf('registerPlugin(NfcPlugin.class)')<activity.indexOf('super.onCreate(savedInstanceState)'));
  const gradle=fs.readFileSync(path.join(dir,'android/app/build.gradle'),'utf8');assert.equal((gradle.match(/robolectric:robolectric/g)||[]).length,1);assert.match(gradle,/versionCode 34/);assert(fs.existsSync(path.join(dir,'android/app/src/test/java/com/chemicalstockmanager/nfc/NfcPluginTest.java')));
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('Fichier web inclus: écriture NFC, annulation et trois données attendues',()=>{
 const html=fs.readFileSync(path.join(root,'Chemical-Stock-Manager-HSE.html'),'utf8');
 for(const match of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g))new vm.Script(match[1]);
 assert(html.includes("registerAction('product-nfc-write'"));assert(html.includes("registerAction('product-nfc-cancel'"));assert(html.includes("'Produit : '+product.nom+'\\nCode : '+product.id+'\\nStockage : '"));
 assert(!html.includes("{text:[product.emplacement||'Stockage non renseigné'"));
});
