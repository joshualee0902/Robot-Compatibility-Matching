package com.joshualee0902.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Save the match, then export/import CSV. Export uses the share sheet (FileProvider) and
 * import uses the system file picker, so no storage or Bluetooth permissions are needed.
 */
public class Screen6 extends FlowActivity {

    private Database db;
    private Button submit;
    private TextView summary;
    private TextView savedCount;
    private TextView list;
    private boolean saved;

    private ActivityResultLauncher<String[]> picker;

    @Override
    protected int layoutId() {
        return R.layout.activity_screen6;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new Database(this);
        submit = findViewById(R.id.submit);
        summary = findViewById(R.id.summary);
        savedCount = findViewById(R.id.savedCount);
        list = findViewById(R.id.final_product);

        saved = savedInstanceState != null && savedInstanceState.getBoolean("saved");

        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importFrom);

        submit.setOnClickListener(v -> submitRecord());
        ((Button) findViewById(R.id.exportCsv)).setOnClickListener(v -> exportCsv());
        ((Button) findViewById(R.id.importCsv)).setOnClickListener(v ->
                picker.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"}));
        ((Button) findViewById(R.id.home)).setOnClickListener(v -> finishFlow());
        start();
    }

    @Override
    protected void readInputs() {
        // nothing to read; this screen only shows the record
    }

    @Override
    protected void populate() {
        summary.setText(getString(R.string.team_num) + " " + record.teamNumber + "   "
                + getString(R.string.match_num) + " " + record.matchNumber + "   "
                + record.outcome + "   " + getString(R.string.total_points) + ": " + record.totalPoints);
        submit.setEnabled(!saved);
        refreshList();
    }

    private void refreshList() {
        List<MatchRecord> all = db.getAll();
        savedCount.setText(getString(R.string.saved_count, all.size()));
        // Display the same CSV that export produces, so what is shown is what is exported.
        list.setText(CsvCodec.encode(all));
    }

    private void submitRecord() {
        if (saved) return; // guards against double-insert
        record.recomputeTotal();
        List<String> errors = record.validate();
        if (!errors.isEmpty()) {
            Toast.makeText(this, joinErrors(errors), Toast.LENGTH_LONG).show();
            return;
        }
        if (db.insert(record)) {
            saved = true;
            submit.setEnabled(false);
            Toast.makeText(this, "Data Inserted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Data Not Inserted", Toast.LENGTH_LONG).show();
        }
        refreshList();
    }

    private void exportCsv() {
        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create export folder");
            File file = new File(dir, "scouting_data.csv");
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(CsvCodec.encode(db.getAll()).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.export_csv)));
        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importFrom(Uri uri) {
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open file");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            }
            int[] result = db.importRecords(CsvCodec.decode(sb.toString()));
            Toast.makeText(this, "Imported " + result[0] + ", skipped " + result[1] + " duplicates",
                    Toast.LENGTH_LONG).show();
            refreshList();
        } catch (IOException | CsvCodec.CsvException e) {
            // Nothing is inserted if the file is invalid: decode() fails before any write.
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("saved", saved);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
