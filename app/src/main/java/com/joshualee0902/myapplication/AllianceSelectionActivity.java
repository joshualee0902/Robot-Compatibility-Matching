package com.joshualee0902.myapplication;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ranks alliance partners offline: model_params.json (bundled asset) + the imported TBA snapshot
 * and local scouting data (SQLite). "Pick" adds a team to our alliance, "Gone" removes a team
 * that someone else took or that declined; the list re-ranks immediately.
 */
public class AllianceSelectionActivity extends AppCompatActivity {

    private static final String ASSET = "model_params.json";
    private static final String STATE_CAPTAIN = "captain";
    private static final String STATE_PICKS = "picks";
    private static final String STATE_GONE = "gone";

    private Database db;
    private ModelParams params;
    private String modelProblem;
    private FeatureBuilder.Table table;
    private PartnerMatcher.Draft draft;
    private int captain;
    private final ArrayList<Integer> picks = new ArrayList<>();
    private final ArrayList<Integer> gone = new ArrayList<>();

    private EditText captainInput;
    private TextView status;
    private TextView modelInfo;
    private CandidateAdapter adapter;
    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alliance_selection);
        db = new Database(this);
        loadModel();

        captainInput = findViewById(R.id.captainTeam);
        status = findViewById(R.id.draftStatus);
        modelInfo = findViewById(R.id.modelInfo);
        adapter = new CandidateAdapter(new ArrayList<PartnerMatcher.Ranked>());
        ((ListView) findViewById(R.id.candidateList)).setAdapter(adapter);

        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importSnapshot);
        ((Button) findViewById(R.id.importSnapshot)).setOnClickListener(v ->
                picker.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"}));
        ((Button) findViewById(R.id.startDraft)).setOnClickListener(v -> {
            picks.clear();
            gone.clear();
            startDraft();
        });
        ((Button) findViewById(R.id.resetDraft)).setOnClickListener(v -> {
            picks.clear();
            gone.clear();
            startDraft();
        });

        if (params == null) {
            modelInfo.setText(modelProblem);
        } else {
            String note = "fitted".equals(params.source) ? "" : "  WARNING: model source is '" + params.source + "'";
            modelInfo.setText("Model: " + params.features + note);
        }

        if (savedInstanceState != null) {
            captain = savedInstanceState.getInt(STATE_CAPTAIN);
            picks.addAll(savedInstanceState.getIntegerArrayList(STATE_PICKS));
            gone.addAll(savedInstanceState.getIntegerArrayList(STATE_GONE));
            if (captain > 0) {
                captainInput.setText(String.valueOf(captain));
                startDraft();
            }
        }
    }

    private void loadModel() {
        try (InputStream in = getAssets().open(ASSET)) {
            params = ModelParams.fromJson(readAll(in));
        } catch (IOException e) {
            modelProblem = "No model_params.json is bundled in this build. Run `make export` (needs `make data`) and rebuild.";
        } catch (IllegalArgumentException e) {
            modelProblem = e.getMessage();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private void importSnapshot(Uri uri) {
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open file");
            SnapshotCsv.Snapshot s = SnapshotCsv.parse(readAll(in));
            db.replaceSnapshot(s.eventKey, s.ratings);
            Toast.makeText(this, "Imported " + s.ratings.size() + " teams for " + s.eventKey, Toast.LENGTH_LONG).show();
        } catch (IOException | CsvCodec.CsvException e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startDraft() {
        if (params == null) {
            status.setText(modelProblem);
            return;
        }
        try {
            captain = Integer.parseInt(captainInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            status.setText("Enter your team number.");
            return;
        }
        if (db.getSnapshot().isEmpty()) {
            status.setText("Import a TBA snapshot first (python -m analytics.export --snapshot EVENT).");
            return;
        }
        table = FeatureBuilder.build(params, db.getSnapshot(), db.getAll());
        if (table.indexOf(captain) < 0) {
            status.setText("Team " + captain + " is not in the imported snapshot.");
            adapter.replace(new ArrayList<PartnerMatcher.Ranked>());
            return;
        }
        List<Integer> pool = new ArrayList<>();
        for (int t : table.teams) pool.add(t);
        draft = new PartnerMatcher.Draft(table, params, captain, pool);
        // replay so rotation keeps the draft
        for (int t : new ArrayList<>(gone)) {
            try { draft.decline(t); } catch (IllegalArgumentException ignored) { gone.remove((Integer) t); }
        }
        for (int t : new ArrayList<>(picks)) {
            try { draft.pick(t); } catch (IllegalArgumentException ignored) { picks.remove((Integer) t); }
        }
        refresh();
    }

    private void refresh() {
        List<PartnerMatcher.Ranked> ranked = draft.recommend(0);
        adapter.replace(ranked);
        String mine = picks.isEmpty() ? "none yet" : picks.toString();
        status.setText(String.format(Locale.US, "Captain %d. Picks: %s. %d pick(s) left, %d candidates.",
                captain, mine, draft.remainingPicks(), ranked.size()));
    }

    private String why(PartnerMatcher.Ranked r) {
        double total = 0;
        for (double c : r.contributions) total += c;
        StringBuilder z = new StringBuilder();
        int worst = 0, best = 0;
        double[] row = table.row(r.team);
        for (int j = 0; j < row.length; j++) {
            if (j > 0) z.append(", ");
            z.append(String.format(Locale.US, "%s %+.2f", table.names[j], row[j]));
            if (r.contributions[j] > r.contributions[worst]) worst = j;
            if (r.contributions[j] < r.contributions[best]) best = j;
        }
        String share = total > 0 ? String.format(Locale.US, "%.0f%%", 100 * r.contributions[worst] / total) : "0%";
        return z + "\nbest fit: " + table.names[best] + "; furthest from target: " + table.names[worst] + " (" + share + " of distance)";
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt(STATE_CAPTAIN, draft == null ? 0 : captain);
        out.putIntegerArrayList(STATE_PICKS, picks);
        out.putIntegerArrayList(STATE_GONE, gone);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private class CandidateAdapter extends ArrayAdapter<PartnerMatcher.Ranked> {
        CandidateAdapter(List<PartnerMatcher.Ranked> items) {
            super(AllianceSelectionActivity.this, 0, new ArrayList<>(items));
        }

        void replace(List<PartnerMatcher.Ranked> items) {
            clear();
            addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(int position, View convert, @NonNull ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(getContext()).inflate(R.layout.row_candidate, parent, false);
            final PartnerMatcher.Ranked r = getItem(position);
            ((TextView) v.findViewById(R.id.candTitle)).setText(
                    String.format(Locale.US, "#%d  Team %d   distance %.2f", position + 1, r.team, r.distance));
            ((TextView) v.findViewById(R.id.candWhy)).setText(why(r));
            v.findViewById(R.id.candPick).setOnClickListener(x -> {
                draft.pick(r.team);
                picks.add(r.team);
                refresh();
            });
            v.findViewById(R.id.candGone).setOnClickListener(x -> {
                draft.decline(r.team);
                gone.add(r.team);
                refresh();
            });
            return v;
        }
    }
}
