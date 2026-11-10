package com.joshualee0902.myapplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Learned parameters exported by analytics/export.py (model_params.json, schema_version 1). */
public final class ModelParams {
    public static final int SUPPORTED_SCHEMA = 1;
    public static final int ALLIANCE_SIZE = 3;

    public final String source;
    public final List<String> features;
    public final double[] weights;
    public final double[] winningProfile;
    public final double scoutingPriorStrength;

    private ModelParams(String source, List<String> features, double[] weights, double[] winningProfile, double m) {
        this.source = source;
        this.features = features;
        this.weights = weights;
        this.winningProfile = winningProfile;
        this.scoutingPriorStrength = m;
    }

    public static ModelParams fromJson(String json) {
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid model_params.json: " + e.getMessage(), e);
        }
    }

    public static ModelParams parse(JSONObject o) throws JSONException {
        int schema = o.getInt("schema_version");
        if (schema != SUPPORTED_SCHEMA) {
            throw new IllegalArgumentException("Unsupported model schema_version " + schema);
        }
        if (o.optInt("alliance_size", ALLIANCE_SIZE) != ALLIANCE_SIZE) {
            throw new IllegalArgumentException("alliance_size must be " + ALLIANCE_SIZE);
        }
        List<String> features = new ArrayList<>();
        JSONArray f = o.getJSONArray("features");
        for (int i = 0; i < f.length(); i++) {
            String name = f.getString(i);
            if (!FeatureBuilder.isKnown(name)) throw new IllegalArgumentException("Unknown feature: " + name);
            features.add(name);
        }
        double[] weights = doubles(o.getJSONArray("weights"));
        double[] profile = doubles(o.getJSONArray("winning_profile"));
        if (weights.length != features.size() || profile.length != features.size()) {
            throw new IllegalArgumentException("weights / winning_profile length must match features");
        }
        for (double w : weights) {
            if (w < 0 || Double.isNaN(w)) throw new IllegalArgumentException("weights must be non-negative");
        }
        return new ModelParams(o.optString("source", ""), features, weights, profile,
                o.optDouble("scouting_prior_strength", 5.0));
    }

    static double[] doubles(JSONArray a) throws JSONException {
        double[] out = new double[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.getDouble(i);
        return out;
    }

    @Override
    public String toString() {
        return "ModelParams{source=" + source + ", features=" + features + ", weights=" + Arrays.toString(weights) + "}";
    }
}
