package com.autoreport.gemini;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 7001;
    private static final String PREFS = "ai_report_prefs";
    private static final String MODEL_PRIMARY = "gemini-3.8-flash";
    private static final String MODEL_FALLBACK = "gemini-3.5-flash-lite";

    private EditText apiKey, region, district, ps, date, time, subject, rawText;
    private Spinner reportType, voiceLang;
    private TextView apiStatus, status, output;
    private ProgressBar progress;
    private Button generateBtn;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String[] reportTypes = new String[] {
            "Political",
            "Religious",
            "Security",
            "Search Operation",
            "Road Accident",
            "Crime / Incident",
            "Recovery / Seizure",
            "Law & Order",
            "Missing Person",
            "Protest / Demonstration",
            "Miscellaneous"
    };

    private final String[] voiceLabels = new String[] {
            "Voice: Urdu (Pakistan)",
            "Voice: English (Pakistan)",
            "Voice: English (US)"
    };
    private final String[] voiceCodes = new String[] {"ur-PK", "en-PK", "en-US"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        apiKey = findViewById(R.id.apiKey);
        region = findViewById(R.id.region);
        district = findViewById(R.id.district);
        ps = findViewById(R.id.ps);
        date = findViewById(R.id.date);
        time = findViewById(R.id.time);
        subject = findViewById(R.id.subject);
        rawText = findViewById(R.id.rawText);
        reportType = findViewById(R.id.reportType);
        voiceLang = findViewById(R.id.voiceLang);
        apiStatus = findViewById(R.id.apiStatus);
        status = findViewById(R.id.status);
        output = findViewById(R.id.output);
        progress = findViewById(R.id.progress);
        generateBtn = findViewById(R.id.generateBtn);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reportTypes);
        reportType.setAdapter(typeAdapter);
        reportType.setSelection(reportTypes.length - 1);

        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels);
        voiceLang.setAdapter(voiceAdapter);

        apiKey.setText(prefs.getString("apiKey", ""));
        region.setText(prefs.getString("region", "Sargodha"));
        district.setText(prefs.getString("district", "Khushab"));
        ps.setText(prefs.getString("ps", ""));

        setCurrentDateTime();

        date.setOnClickListener(v -> pickDate());
        time.setOnClickListener(v -> pickTime());

        findViewById(R.id.getKeyBtn).setOnClickListener(v -> {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(browser);
        });

        findViewById(R.id.saveKeyBtn).setOnClickListener(v -> {
            saveBasics();
            testApiKey();
        });

        findViewById(R.id.voiceBtn).setOnClickListener(v -> startVoiceInput());
        generateBtn.setOnClickListener(v -> generateReport());
        findViewById(R.id.copyBtn).setOnClickListener(v -> copyReport());
        findViewById(R.id.whatsappBtn).setOnClickListener(v -> shareWhatsApp());
        findViewById(R.id.shareBtn).setOnClickListener(v -> shareGeneral());
        findViewById(R.id.saveReportBtn).setOnClickListener(v -> saveReport());
        findViewById(R.id.historyBtn).setOnClickListener(v -> showHistory());

        apiStatus.setText(apiKey.getText().toString().trim().isEmpty()
                ? "Paste a Gemini API key, then Save / Test Key."
                : "Key saved locally • Model: " + MODEL_PRIMARY);
    }

    private void saveBasics() {
        prefs.edit()
                .putString("apiKey", apiKey.getText().toString().trim())
                .putString("region", region.getText().toString().trim())
                .putString("district", district.getText().toString().trim())
                .putString("ps", ps.getText().toString().trim())
                .apply();
    }

    private void setCurrentDateTime() {
        date.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()));
        time.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date()));
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this, (view, year, month, day) ->
                date.setText(String.format(Locale.US, "%02d-%02d-%04d", day, month + 1, year)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private void pickTime() {
        Calendar c = Calendar.getInstance();
        TimePickerDialog dlg = new TimePickerDialog(this, (view, hour, minute) -> {
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, minute);
            time.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(t.getTime()));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false);
        dlg.show();
    }

    private void startVoiceInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            int pos = Math.max(0, voiceLang.getSelectedItemPosition());
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceCodes[pos]);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak report details");
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) {
            toast("Voice recognition is not available on this device.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && dataIntent != null) {
            ArrayList<String> results = dataIntent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String existing = rawText.getText().toString().trim();
                String heard = results.get(0).trim();
                rawText.setText(existing.isEmpty() ? heard : existing + "\n" + heard);
                rawText.setSelection(rawText.getText().length());
            }
        }
    }

    private void testApiKey() {
        String key = apiKey.getText().toString().trim();
        if (key.isEmpty()) {
            setStatus("Paste your Gemini API key first.", true);
            return;
        }
        setLoading(true, "Testing Gemini API key...");
        executor.execute(() -> {
            try {
                String result = callGemini(key, MODEL_PRIMARY,
                        "Reply with exactly: GEMINI_CONNECTED");
                boolean ok = result.toUpperCase(Locale.US).contains("GEMINI_CONNECTED");
                runOnUiThread(() -> {
                    setLoading(false, "");
                    if (ok) {
                        apiStatus.setText("✓ Gemini connected • Model: " + MODEL_PRIMARY);
                        apiStatus.setTextColor(Color.parseColor("#1D6145"));
                        setStatus("API key is working.", false);
                    } else {
                        setStatus("Gemini responded, but test output was unexpected. Try Generate.", false);
                    }
                });
            } catch (Exception first) {
                // If the newest model is not available for the key/project, test the cheaper fallback.
                try {
                    String result = callGemini(key, MODEL_FALLBACK,
                            "Reply with exactly: GEMINI_CONNECTED");
                    boolean ok = result.toUpperCase(Locale.US).contains("GEMINI_CONNECTED");
                    runOnUiThread(() -> {
                        setLoading(false, "");
                        if (ok) {
                            apiStatus.setText("✓ Gemini connected • Fallback model available: " + MODEL_FALLBACK);
                            apiStatus.setTextColor(Color.parseColor("#1D6145"));
                            setStatus("API key is working.", false);
                        } else {
                            setStatus("Gemini key test returned an unexpected response.", true);
                        }
                    });
                } catch (Exception second) {
                    runOnUiThread(() -> {
                        setLoading(false, "");
                        setStatus("API test failed: " + friendlyError(second), true);
                    });
                }
            }
        });
    }

    private void generateReport() {
        saveBasics();
        String key = apiKey.getText().toString().trim();
        String raw = rawText.getText().toString().trim();

        if (key.isEmpty()) {
            setStatus("First paste your Gemini API key and press Save / Test Key.", true);
            return;
        }
        if (raw.isEmpty()) {
            setStatus("Write or speak 3–4 lines of report details first.", true);
            return;
        }

        final String prompt = buildPrompt();
        setLoading(true, "Gemini is translating and drafting the report...");

        executor.execute(() -> {
            String modelUsed = MODEL_PRIMARY;
            try {
                String result;
                try {
                    result = callGemini(key, MODEL_PRIMARY, prompt);
                } catch (ApiHttpException e) {
                    if (e.code == 404 || e.code == 400) {
                        modelUsed = MODEL_FALLBACK;
                        result = callGemini(key, MODEL_FALLBACK, prompt);
                    } else {
                        throw e;
                    }
                }
                final String finalResult = cleanModelOutput(result);
                final String finalModelUsed = modelUsed;

                runOnUiThread(() -> {
                    setLoading(false, "");
                    if (finalResult.isEmpty()) {
                        setStatus("Gemini returned an empty report. Please try again.", true);
                        return;
                    }
                    output.setText(finalResult);
                    apiStatus.setText("✓ Connected • Model used: " + finalModelUsed);
                    setStatus("Report generated successfully.", false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    setStatus("Generation failed: " + friendlyError(e), true);
                });
            }
        });
    }

    private String buildPrompt() {
        String type = reportType.getSelectedItem().toString();
        String r = region.getText().toString().trim();
        String d = district.getText().toString().trim();
        String station = ps.getText().toString().trim();
        String dt = date.getText().toString().trim();
        String tm = time.getText().toString().trim();
        String sub = subject.getText().toString().trim();
        String raw = rawText.getText().toString().trim();

        return "You are a professional English field-report drafting assistant.\n\n"
                + "The user may give only 3 or 4 short lines in Urdu script, Roman Urdu, broken English, or a mixture. "
                + "Translate and rewrite them into polished, natural, formal English. Expand the wording and sentence structure "
                + "like an experienced official report writer, while preserving the user's facts exactly.\n\n"
                + "REPORT TYPE: " + type + "\n"
                + "REGION: " + emptyAsDash(r) + "\n"
                + "DISTRICT: " + emptyAsDash(d) + "\n"
                + "POLICE STATION: " + emptyAsDash(station) + "\n"
                + "DATE: " + emptyAsDash(dt) + "\n"
                + "TIME: " + emptyAsDash(tm) + "\n"
                + "USER SUBJECT: " + (sub.isEmpty() ? "[create a suitable subject]" : sub) + "\n\n"
                + "RAW NOTES:\n" + raw + "\n\n"
                + "RULES:\n"
                + "1. Translate Urdu/Roman Urdu to fluent professional English.\n"
                + "2. Add strong professional report-writing language, transitions, and category-appropriate neutral wording.\n"
                + "3. Do not invent substantive facts. Do not invent names, numbers, locations, affiliations, injuries, deaths, "
                + "arrests, recoveries, FIR numbers, weapons, motives, police actions, security deployment, legal action, or outcomes.\n"
                + "4. Preserve names, s/o, d/o, ages, castes, addresses, organizations, numbers and places accurately.\n"
                + "5. If a fact is not in the notes, omit it rather than guessing.\n"
                + "6. For Political and Religious reports, remain strictly neutral, factual and non-persuasive.\n"
                + "7. For Security and Search Operation reports, do not add operational tactics or sensitive procedures not supplied by the user.\n"
                + "8. Put time naturally in the first paragraph; do not add a separate Time metadata line.\n"
                + "9. Create a concise uppercase SUBJECT when the subject is blank.\n"
                + "10. Write 2 to 4 short professional paragraphs when the facts allow it. It is acceptable to add non-factual formal phrases "
                + "such as 'It is submitted that' and 'The report is submitted for kind information, please.'\n"
                + "11. Do not use markdown, bullets, code fences, explanations, or notes outside the report.\n\n"
                + "OUTPUT EXACTLY IN THIS STRUCTURE:\n\n"
                + "SUBJECT: [UPPERCASE SUBJECT]\n\n"
                + "Region: " + emptyAsDash(r) + "\n"
                + "District: " + emptyAsDash(d) + "\n"
                + "PS: " + emptyAsDash(station) + "\n"
                + "Date: " + emptyAsDash(dt) + "\n\n"
                + "Respected Sir,\n\n"
                + "[professional report paragraphs]\n\n"
                + "Regards.\n\n"
                + "Return only the final report.";
    }

    private String callGemini(String key, String model, String prompt) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("x-goog-api-key", key);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        String response = readAll(stream);

        if (code < 200 || code >= 300) {
            throw new ApiHttpException(code, extractApiMessage(response));
        }

        JSONObject root = new JSONObject(response);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            String blockReason = root.optJSONObject("promptFeedback") != null
                    ? root.optJSONObject("promptFeedback").optString("blockReason", "")
                    : "";
            if (!blockReason.isEmpty()) {
                throw new Exception("Request blocked by Gemini safety policy: " + blockReason);
            }
            throw new Exception("No text candidate was returned.");
        }

        JSONObject c0 = candidates.getJSONObject(0);
        JSONObject responseContent = c0.optJSONObject("content");
        if (responseContent == null) throw new Exception("Gemini response had no content.");

        JSONArray responseParts = responseContent.optJSONArray("parts");
        if (responseParts == null) throw new Exception("Gemini response had no text parts.");

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < responseParts.length(); i++) {
            JSONObject part = responseParts.optJSONObject(i);
            if (part != null && !part.optBoolean("thought", false)) {
                String t = part.optString("text", "");
                if (!t.isEmpty()) text.append(t);
            }
        }
        return text.toString().trim();
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String extractApiMessage(String response) {
        try {
            JSONObject root = new JSONObject(response);
            JSONObject error = root.optJSONObject("error");
            if (error != null) return error.optString("message", response);
        } catch (Exception ignored) {}
        return response == null || response.isEmpty() ? "Unknown API error" : response;
    }

    private String cleanModelOutput(String s) {
        if (s == null) return "";
        String out = s.trim();
        if (out.startsWith("```")) {
            out = out.replaceFirst("^```[a-zA-Z]*\\s*", "");
            out = out.replaceFirst("\\s*```$", "");
        }
        return out.trim();
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String lower = msg.toLowerCase(Locale.US);
        if (lower.contains("api key not valid") || lower.contains("invalid api key")) {
            return "API key is invalid. Create/copy a new Gemini key from Google AI Studio.";
        }
        if (lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("429")) {
            return "Gemini free-tier quota/rate limit was reached. Try again later or check AI Studio usage.";
        }
        if (lower.contains("permission") || lower.contains("403")) {
            return "This API key/project does not have Gemini API permission. Create a new Gemini API key in AI Studio.";
        }
        return msg.length() > 280 ? msg.substring(0, 280) + "…" : msg;
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        generateBtn.setEnabled(!loading);
        if (!message.isEmpty()) setStatus(message, false);
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(Color.parseColor(error ? "#9F3030" : "#1D6145"));
    }

    private boolean hasReport() {
        String s = output.getText().toString().trim();
        return !s.isEmpty() && !s.startsWith("Your generated report");
    }

    private void copyReport() {
        if (!hasReport()) {
            toast("Generate a report first.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Incident Report", output.getText().toString()));
        toast("Report copied.");
    }

    private void shareWhatsApp() {
        if (!hasReport()) {
            toast("Generate a report first.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
        send.setPackage("com.whatsapp");
        try {
            startActivity(send);
        } catch (Exception e) {
            send.setPackage(null);
            startActivity(Intent.createChooser(send, "Share report"));
        }
    }

    private void shareGeneral() {
        if (!hasReport()) {
            toast("Generate a report first.");
            return;
        }
        try {
            File dir = new File(getCacheDir(), "shared_reports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "incident-report-" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(output.getText().toString().getBytes(StandardCharsets.UTF_8));
            }

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, firstLine(output.getText().toString()));
            send.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("Incident Report", uri));
            startActivity(Intent.createChooser(send, "Share / backup report"));
        } catch (Exception e) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
            startActivity(Intent.createChooser(send, "Share report"));
        }
    }

    private void saveReport() {
        if (!hasReport()) {
            toast("Generate a report first.");
            return;
        }
        try {
            JSONArray arr = new JSONArray(prefs.getString("history", "[]"));
            JSONObject item = new JSONObject();
            item.put("id", System.currentTimeMillis());
            item.put("title", firstLine(output.getText().toString()).replace("SUBJECT:", "").trim());
            item.put("text", output.getText().toString());
            item.put("savedAt", new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(new Date()));

            JSONArray next = new JSONArray();
            next.put(item);
            for (int i = 0; i < arr.length() && i < 29; i++) next.put(arr.getJSONObject(i));
            prefs.edit().putString("history", next.toString()).apply();
            toast("Report saved on this phone.");
        } catch (Exception e) {
            toast("Could not save report.");
        }
    }

    private void showHistory() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("history", "[]"));
            if (arr.length() == 0) {
                toast("No saved reports yet.");
                return;
            }

            String[] labels = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                labels[i] = o.optString("title", "Report") + "\n" + o.optString("savedAt", "");
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Saved Reports")
                    .setItems(labels, (d, which) -> {
                        try {
                            JSONObject o = arr.getJSONObject(which);
                            output.setText(o.optString("text", ""));
                            setStatus("Saved report opened.", false);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Clear All", null)
                    .create();

            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Clear saved reports?")
                        .setMessage("This will remove all locally saved reports.")
                        .setPositiveButton("Clear", (x, y) -> {
                            prefs.edit().remove("history").apply();
                            dialog.dismiss();
                            toast("Saved reports cleared.");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }));
            dialog.show();
        } catch (Exception e) {
            toast("Could not open saved reports.");
        }
    }

    private String firstLine(String s) {
        int n = s.indexOf('\n');
        return n >= 0 ? s.substring(0, n) : s;
    }

    private String emptyAsDash(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s.trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static class ApiHttpException extends Exception {
        final int code;
        ApiHttpException(int code, String message) {
            super("HTTP " + code + ": " + message);
            this.code = code;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
