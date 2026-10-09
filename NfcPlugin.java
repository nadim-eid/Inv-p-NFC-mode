package com.chemicalstockmanager.nfc;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local NFC bridge. No external plugin, account, subscription or network access. */
@CapacitorPlugin(name = "Nfc")
public class NfcPlugin extends Plugin {
    private NfcAdapter adapter;
    private final Object sessionLock = new Object();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService closer = Executors.newSingleThreadExecutor();
    private boolean scanning = false;
    private boolean writing = false;
    private long generation = 0;
    private Tag currentTag;
    private TagTechnology connection;

    @Override public void load() { adapter = NfcAdapter.getDefaultAdapter(getContext()); }

    @PluginMethod public void isSupported(PluginCall call) {
        JSObject result = new JSObject(); result.put("nfc", adapter != null); call.resolve(result);
    }
    @PluginMethod public void isEnabled(PluginCall call) {
        JSObject result = new JSObject(); result.put("isEnabled", adapter != null && adapter.isEnabled()); call.resolve(result);
    }
    @PluginMethod public void startScanSession(PluginCall call) {
        if (adapter == null) { call.reject("Cet appareil ne possède pas de NFC."); return; }
        if (!adapter.isEnabled()) { call.reject("Activez le NFC dans les paramètres Android."); return; }
        getActivity().runOnUiThread(() -> {
            synchronized (sessionLock) {
                if (scanning || writing) { call.reject("Une session NFC est déjà active."); return; }
                scanning = true; currentTag = null; generation++;
            }
            try {
                Bundle options = new Bundle();
                options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
                // Do not skip the NDEF check: NTAG215 must expose its Ndef technology.
                adapter.enableReaderMode(getActivity(), this::tagDiscovered,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V, options);
                call.resolve();
            } catch (Exception error) { stopSession(); call.reject("Démarrage NFC impossible : " + error.getMessage()); }
        });
    }
    private void tagDiscovered(Tag tag) {
        synchronized (sessionLock) {
            if (!scanning || writing || currentTag != null) return;
            currentTag = tag;
            JSObject info = new JSObject();
            info.put("id", bytesToJson(tag.getId()));
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) { info.put("isWritable", ndef.isWritable()); info.put("maxSize", ndef.getMaxSize()); }
            else info.put("isWritable", NdefFormatable.get(tag) != null);
            JSObject event = new JSObject(); event.put("nfcTag", info);
            notifyListeners("nfcTagScanned", event);
        }
    }
    @PluginMethod public void stopScanSession(PluginCall call) {
        getActivity().runOnUiThread(() -> { stopSession(); call.resolve(); });
    }
    @PluginMethod public void write(PluginCall call) {
        final NdefMessage message;
        try { message = decodeMessage(call.getObject("message")); }
        catch (Exception error) { call.reject("Données NFC invalides : " + error.getMessage()); return; }
        final Tag tag; final long token;
        synchronized (sessionLock) {
            if (!scanning || currentTag == null) { call.reject("Approchez un tag pendant une session NFC active."); return; }
            if (writing) { call.reject("Une écriture NFC est déjà en cours."); return; }
            writing = true; tag = currentTag; token = generation;
        }
        io.execute(() -> writeTag(call, tag, token, message));
    }
    private void writeTag(PluginCall call, Tag tag, long token, NdefMessage message) {
        TagTechnology tech = null;
        try {
            requireSession(token);
            Ndef ndef = Ndef.get(tag);
            boolean verified = false;
            if (ndef != null) {
                tech = ndef; setConnection(token, tech); ndef.connect();
                requireSession(token);
                if (!ndef.isWritable()) throw new IllegalStateException("Tag verrouillé en lecture seule.");
                if (message.toByteArray().length > ndef.getMaxSize()) throw new IllegalArgumentException("Capacité du tag NFC insuffisante.");
                ndef.writeNdefMessage(message);
                requireSession(token);
                NdefMessage check = ndef.getNdefMessage();
                if (check == null || !Arrays.equals(message.toByteArray(), check.toByteArray()))
                    throw new IllegalStateException("Le contrôle du tag écrit a échoué. Réessayez l’écriture.");
                verified = true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format == null) throw new IllegalArgumentException("Ce tag ne permet pas l’écriture NDEF.");
                tech = format; setConnection(token, tech); format.connect(); requireSession(token);
                format.format(message); // Never call formatReadOnly or makeReadOnly.
                requireSession(token);
            }
            JSObject result = new JSObject(); result.put("verified", verified); result.put("bytes", message.toByteArray().length);
            // Check and resolve under the same lock: a canceled session cannot report success.
            synchronized (sessionLock) { requireSession(token); call.resolve(result); }
        } catch (Exception error) {
            call.reject("Écriture NFC impossible : " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            closeQuietly(tech);
            synchronized (sessionLock) { if (connection == tech) connection = null; writing = false; }
        }
    }
    private void requireSession(long token) {
        synchronized (sessionLock) {
            if (!scanning || generation != token) throw new IllegalStateException("Écriture NFC annulée.");
        }
    }
    private void setConnection(long token, TagTechnology tech) {
        synchronized (sessionLock) { requireSession(token); connection = tech; }
    }
    private void stopSession() {
        final TagTechnology active;
        synchronized (sessionLock) { scanning = false; generation++; currentTag = null; active = connection; connection = null; }
        if (adapter != null) try { adapter.disableReaderMode(getActivity()); } catch (Exception ignored) {}
        // Closing the connection interrupts blocked tag I/O without blocking the UI.
        if (active != null) closer.execute(() -> closeQuietly(active));
    }
    private static void closeQuietly(TagTechnology tech) { if (tech != null) try { tech.close(); } catch (Exception ignored) {} }
    @Override protected void handleOnPause() {
        boolean active; synchronized (sessionLock) { active = scanning; }
        stopSession();
        if (active) { JSObject error = new JSObject(); error.put("message", "Écriture NFC interrompue : application en arrière-plan."); notifyListeners("scanSessionError", error); }
    }
    @Override protected void handleOnDestroy() { stopSession(); io.shutdownNow(); closer.shutdown(); }

    static NdefMessage decodeMessage(JSObject data) throws Exception {
        if (data == null) throw new IllegalArgumentException("Message absent.");
        JSONArray records = data.optJSONArray("records");
        if (records == null || records.length() != 1) throw new IllegalArgumentException("Un seul enregistrement texte est attendu.");
        JSONObject record = records.getJSONObject(0);
        if (record.getInt("tnf") != NdefRecord.TNF_WELL_KNOWN) throw new IllegalArgumentException("Type NDEF non pris en charge.");
        byte[] type = jsonToBytes(record.getJSONArray("type"));
        byte[] id = jsonToBytes(record.getJSONArray("id"));
        byte[] payload = jsonToBytes(record.getJSONArray("payload"));
        if (!Arrays.equals(type, NdefRecord.RTD_TEXT) || id.length != 0 || payload.length < 3 || payload.length > 481)
            throw new IllegalArgumentException("Enregistrement texte invalide ou trop volumineux pour NTAG215.");
        NdefMessage message = new NdefMessage(new NdefRecord[]{new NdefRecord(NdefRecord.TNF_WELL_KNOWN, type, id, payload)});
        if (message.toByteArray().length > 488) throw new IllegalArgumentException("Données trop longues pour NTAG215.");
        return message;
    }
    static byte[] jsonToBytes(JSONArray data) throws Exception {
        byte[] bytes = new byte[data.length()];
        for (int i = 0; i < bytes.length; i++) {
            Object raw = data.get(i);
            if (!(raw instanceof Number) || ((Number) raw).doubleValue() != ((Number) raw).intValue()) throw new IllegalArgumentException("Octet invalide.");
            int value = ((Number) raw).intValue();
            if (value < 0 || value > 255) throw new IllegalArgumentException("Octet hors plage.");
            bytes[i] = (byte) value;
        }
        return bytes;
    }
    private static JSArray bytesToJson(byte[] data) { JSArray result = new JSArray(); for (byte value : data) result.put(value & 255); return result; }
}
