package com.chemicalstockmanager.nfc;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class NfcPluginTest {
    private JSObject message(String text) {
        JSObject record = new JSObject();
        record.put("tnf", 1); record.put("type", new JSArray().put(84)); record.put("id", new JSArray());
        JSArray payload = new JSArray(); payload.put(2); payload.put(102); payload.put(114);
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) payload.put(b & 255);
        record.put("payload", payload);
        JSObject message = new JSObject(); message.put("records", new JSArray().put(record)); return message;
    }
    @Test public void textAndAccentsSurviveNdefRoundtrip() throws Exception {
        String text = "Produit : Acide éthanoïque\nCode : CHEM-ABC12345\nStockage : Armoire A";
        NdefMessage encoded = NfcPlugin.decodeMessage(message(text));
        NdefRecord record = new NdefMessage(encoded.toByteArray()).getRecords()[0];
        assertEquals(NdefRecord.TNF_WELL_KNOWN, record.getTnf());
        assertArrayEquals(NdefRecord.RTD_TEXT, record.getType());
        assertEquals(text, new String(Arrays.copyOfRange(record.getPayload(), 3, record.getPayload().length), StandardCharsets.UTF_8));
        assertTrue(encoded.toByteArray().length <= 488);
    }
    @Test public void exactNtag215BudgetIsAccepted() throws Exception {
        assertEquals(488, NfcPlugin.decodeMessage(message("a".repeat(478))).toByteArray().length);
    }
    @Test(expected = IllegalArgumentException.class) public void oneByteOverBudgetIsRejected() throws Exception {
        NfcPlugin.decodeMessage(message("a".repeat(479)));
    }
    @Test(expected = IllegalArgumentException.class) public void multibyteNameIsCountedInBytes() throws Exception {
        NfcPlugin.decodeMessage(message("É".repeat(240)));
    }
    @Test(expected = IllegalArgumentException.class) public void twoRecordsAreRejected() throws Exception {
        JSObject message = message("Flacon A");
        JSONArray records = message.getJSONArray("records"); records.put(records.get(0));
        NfcPlugin.decodeMessage(message);
    }
    @Test(expected = IllegalArgumentException.class) public void arbitraryRecordTypeIsRejected() throws Exception {
        JSObject message = message("Flacon A"); message.getJSONArray("records").getJSONObject(0).put("type", new JSArray().put(85));
        NfcPlugin.decodeMessage(message);
    }
    @Test(expected = IllegalArgumentException.class) public void negativeByteIsRejected() throws Exception {
        NfcPlugin.jsonToBytes(new JSArray().put(-1));
    }
    @Test(expected = IllegalArgumentException.class) public void byteOver255IsRejected() throws Exception {
        NfcPlugin.jsonToBytes(new JSArray().put(256));
    }
    @Test(expected = IllegalArgumentException.class) public void fractionalByteIsRejected() throws Exception {
        NfcPlugin.jsonToBytes(new JSArray().put(2.5));
    }
}
