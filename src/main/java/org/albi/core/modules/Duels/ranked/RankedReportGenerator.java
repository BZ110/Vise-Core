package org.albi.core.modules.Duels.ranked;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class RankedReportGenerator {
    private static final DecimalFormat DF_0 = df("0");
    private static final DecimalFormat DF_1 = df("0.0");
    private static final DecimalFormat DF_2 = df("0.00");
    private static final DecimalFormat DF_3 = df("0.000");

    private static DecimalFormat df(String pattern) {
        DecimalFormat f = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        f.setGroupingUsed(false);
        return f;
    }

    public static String generateHtml(RankedMatchRecord record) {
        StringBuilder html = new StringBuilder(180_000);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy HH:mm:ss");
        long start = record.getStartTime();
        long end = record.getEndTime();
        long durationMs = Math.max(1L, end - start);
        int durationSec = Math.max(1, (int) Math.ceil(durationMs / 1000.0));
        List<RankedMatchRecord.HitRecord> allHits = new ArrayList<>(record.getHits());
        allHits.sort(Comparator.comparingLong(RankedMatchRecord.HitRecord::timestamp));
        List<RankedMatchRecord.HitRecord> p1Hits = allHits.stream()
                .filter(h -> h.attacker().equals(record.getPlayer1()))
                .sorted(Comparator.comparingLong(RankedMatchRecord.HitRecord::timestamp)).toList();
        List<RankedMatchRecord.HitRecord> p2Hits = allHits.stream()
                .filter(h -> h.attacker().equals(record.getPlayer2()))
                .sorted(Comparator.comparingLong(RankedMatchRecord.HitRecord::timestamp)).toList();
        List<Long> clicks1 = new ArrayList<>(record.getClicks1());
        List<Long> clicks2 = new ArrayList<>(record.getClicks2());
        Collections.sort(clicks1);
        Collections.sort(clicks2);
        int snapCount = record.getSnapshots().size();
        long[] snapT = new long[snapCount];
        double[] p1Health = new double[snapCount];
        double[] p2Health = new double[snapCount];
        double[] p1Ping = new double[snapCount];
        double[] p2Ping = new double[snapCount];
        double[] p1X = new double[snapCount];
        double[] p1Z = new double[snapCount];
        double[] p2X = new double[snapCount];
        double[] p2Z = new double[snapCount];
        double[] healthDelta = new double[snapCount];
        double[] pingDelta = new double[snapCount];
        double[] playerDistance = new double[snapCount];
        double[] p1SeparationFromStart = new double[snapCount];
        double[] p2SeparationFromStart = new double[snapCount];
        for (int i = 0; i < snapCount; i++) {
            var s = record.getSnapshots().get(i);
            snapT[i] = s.timestamp();
            p1Health[i] = s.p1H();
            p2Health[i] = s.p2H();
            p1Ping[i] = s.p1P();
            p2Ping[i] = s.p2P();
            p1X[i] = s.p1X();
            p1Z[i] = s.p1Z();
            p2X[i] = s.p2X();
            p2Z[i] = s.p2Z();
            healthDelta[i] = p1Health[i] - p2Health[i];
            pingDelta[i] = p1Ping[i] - p2Ping[i];
            playerDistance[i] = hypot(p1X[i] - p2X[i], p1Z[i] - p2Z[i]);
            if (i == 0) {
                p1SeparationFromStart[i] = 0;
                p2SeparationFromStart[i] = 0;
            } else {
                p1SeparationFromStart[i] = hypot(p1X[i] - p1X[0], p1Z[i] - p1Z[0]);
                p2SeparationFromStart[i] = hypot(p2X[i] - p2X[0], p2Z[i] - p2Z[0]);
            }
        }
        double[] snapTimeSec = new double[snapCount];
        for (int i = 0; i < snapCount; i++) {
            snapTimeSec[i] = (snapT[i] - start) / 1000.0;
        }
        double[] p1Speed = speedSeries(p1X, p1Z, snapT);
        double[] p2Speed = speedSeries(p2X, p2Z, snapT);
        double[] p1SpeedSmooth = rollingAverage(p1Speed, 3);
        double[] p2SpeedSmooth = rollingAverage(p2Speed, 3);
        double[] p1Reach = p1Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::distance).toArray();
        double[] p2Reach = p2Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::distance).toArray();
        double[] p1Yaw = p1Hits.stream().mapToDouble(h -> normalizeYaw(h.yaw())).toArray();
        double[] p2Yaw = p2Hits.stream().mapToDouble(h -> normalizeYaw(h.yaw())).toArray();
        double[] p1Pitch = p1Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::pitch).toArray();
        double[] p2Pitch = p2Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::pitch).toArray();
        double[] p1YDiff = p1Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::yDiff).toArray();
        double[] p2YDiff = p2Hits.stream().mapToDouble(RankedMatchRecord.HitRecord::yDiff).toArray();
        double[] p1HitTimeSec = p1Hits.stream().mapToDouble(h -> (h.timestamp() - start) / 1000.0).toArray();
        double[] p2HitTimeSec = p2Hits.stream().mapToDouble(h -> (h.timestamp() - start) / 1000.0).toArray();
        double[] p1HitIntervalsTicks = hitIntervalsTicks(p1Hits, start);
        double[] p2HitIntervalsTicks = hitIntervalsTicks(p2Hits, start);
        int[] p1ClicksPerSec = bucketCounts(clicks1, start, durationSec);
        int[] p2ClicksPerSec = bucketCounts(clicks2, start, durationSec);
        int[] p1HitsPerSec = bucketCounts(p1Hits.stream().map(RankedMatchRecord.HitRecord::timestamp).toList(), start,
                durationSec);
        int[] p2HitsPerSec = bucketCounts(p2Hits.stream().map(RankedMatchRecord.HitRecord::timestamp).toList(), start,
                durationSec);
        int[] p1CumClicks = cumulative(p1ClicksPerSec);
        int[] p2CumClicks = cumulative(p2ClicksPerSec);
        int[] p1CumHits = cumulative(p1HitsPerSec);
        int[] p2CumHits = cumulative(p2HitsPerSec);
        double[] p1CpsSmooth = rollingAverage(toDoubleArray(p1ClicksPerSec), 3);
        double[] p2CpsSmooth = rollingAverage(toDoubleArray(p2ClicksPerSec), 3);
        double[] p1HitSmooth = rollingAverage(toDoubleArray(p1HitsPerSec), 3);
        double[] p2HitSmooth = rollingAverage(toDoubleArray(p2HitsPerSec), 3);
        double[] p1Conv = rollingRatio(p1HitsPerSec, p1ClicksPerSec, 3);
        double[] p2Conv = rollingRatio(p2HitsPerSec, p2ClicksPerSec, 3);
        double[] separationBySec = valueBySecond(snapT, playerDistance, start, durationSec, avg(playerDistance));
        double[] healthDeltaBySec = valueBySecond(snapT, healthDelta, start, durationSec, avg(healthDelta));
        double[] pingDeltaBySec = valueBySecond(snapT, pingDelta, start, durationSec, avg(pingDelta));
        double[] p1ReachBySec = averageHitMetricBySecond(p1Hits, start, durationSec,
                RankedMatchRecord.HitRecord::distance);
        double[] p2ReachBySec = averageHitMetricBySecond(p2Hits, start, durationSec,
                RankedMatchRecord.HitRecord::distance);
        double[] healthSwing = differenceSeries(healthDeltaBySec);
        double[] p1Pressure = new double[durationSec + 1];
        double[] p2Pressure = new double[durationSec + 1];
        double[] momentum = new double[durationSec + 1];
        for (int i = 0; i <= durationSec; i++) {
            double proximityFactor = clamp((4.25 - separationBySec[i]) / 2.2, 0, 1.75);
            double p1Swing = Math.max(0, healthSwing[i]);
            double p2Swing = Math.max(0, -healthSwing[i]);
            p1Pressure[i] = (p1HitsPerSec[i] * 3.0) + (p1CpsSmooth[i] * 0.75) + (p1Conv[i] * 2.2) + (p1Swing * 0.85)
                    + (proximityFactor * 0.6);
            p2Pressure[i] = (p2HitsPerSec[i] * 3.0) + (p2CpsSmooth[i] * 0.75) + (p2Conv[i] * 2.2) + (p2Swing * 0.85)
                    + (proximityFactor * 0.6);
            momentum[i] = (p1Pressure[i] - p2Pressure[i]) + (healthDeltaBySec[i] * 0.55);
        }
        double[] momentumSmooth = rollingAverage(momentum, 3);
        double[] pressureDiff = subtract(p1Pressure, p2Pressure);
        String[] secLabels = secondLabels(durationSec);
        String[] snapLabels = snapTimeLabels(snapTimeSec);
        double[] p1ReachRolling = rollingAverage(p1Reach, 4);
        double[] p2ReachRolling = rollingAverage(p2Reach, 4);
        double avgReach1 = avg(p1Reach);
        double avgReach2 = avg(p2Reach);
        double sdReach1 = stdDev(p1Reach);
        double sdReach2 = stdDev(p2Reach);
        double p95Reach1 = percentile(p1Reach, 95);
        double p95Reach2 = percentile(p2Reach, 95);
        double maxReach1 = max(p1Reach);
        double maxReach2 = max(p2Reach);
        double avgPitch1 = avg(p1Pitch);
        double avgPitch2 = avg(p2Pitch);
        double pitchSpread1 = stdDev(p1Pitch);
        double pitchSpread2 = stdDev(p2Pitch);
        double yCombat1 = avgAbs(p1YDiff);
        double yCombat2 = avgAbs(p2YDiff);
        double avgPing1 = avg(p1Ping);
        double avgPing2 = avg(p2Ping);
        double jitter1 = stdDev(p1Ping);
        double jitter2 = stdDev(p2Ping);
        double avgSpeed1 = avg(p1Speed);
        double avgSpeed2 = avg(p2Speed);
        double maxSpeed1 = max(p1Speed);
        double maxSpeed2 = max(p2Speed);
        double p1Path = pathLength(p1X, p1Z);
        double p2Path = pathLength(p2X, p2Z);
        double avgSeparation = avg(playerDistance);
        double minSeparation = min(playerDistance);
        double maxSeparation = max(playerDistance);
        double p1HitRate = p1Hits.size() / (durationMs / 1000.0);
        double p2HitRate = p2Hits.size() / (durationMs / 1000.0);
        double p1ClickRate = clicks1.size() / (durationMs / 1000.0);
        double p2ClickRate = clicks2.size() / (durationMs / 1000.0);
        double p1DamagePerHit = record.getDamage1() / Math.max(1, p1Hits.size());
        double p2DamagePerHit = record.getDamage2() / Math.max(1, p2Hits.size());
        double p1DamagePerClick = record.getDamage1() / Math.max(1, clicks1.size());
        double p2DamagePerClick = record.getDamage2() / Math.max(1, clicks2.size());
        double p1MechanicalConsistency = computeConsistency(avgReach1, sdReach1, p1HitIntervalsTicks, jitter1);
        double p2MechanicalConsistency = computeConsistency(avgReach2, sdReach2, p2HitIntervalsTicks, jitter2);
        double p1TempoPeak = max(p1Pressure);
        double p2TempoPeak = max(p2Pressure);
        double p1CpsPeak = max(toDoubleArray(p1ClicksPerSec));
        double p2CpsPeak = max(toDoubleArray(p2ClicksPerSec));
        int p1LeadFrames = countGreaterThan(healthDelta, 0);
        int p2LeadFrames = countLessThan(healthDelta, 0);
        int tiedFrames = Math.max(0, snapCount - p1LeadFrames - p2LeadFrames);
        double[] bandEdges = new double[] {
                0.0,
                2.0,
                2.5,
                3.0,
                3.25,
                3.5,
                4.0,
                6.0
        };
        String[] bandLabels = bandLabels(bandEdges);
        int[] p1HitBands = histogramByEdges(p1Reach, bandEdges);
        int[] p2HitBands = histogramByEdges(p2Reach, bandEdges);
        int[] timeBands = histogramByEdges(playerDistance, bandEdges);
        int[] p1PitchHist = histogram(p1Pitch, -90, 90, 15);
        int[] p2PitchHist = histogram(p2Pitch, -90, 90, 15);
        String pitchLabels = jsQuoted(binLabels(-90, 90, 15));
        List<WindowStats> windows = buildWindows(record, durationSec, separationBySec, healthDeltaBySec, healthSwing,
                p1ClicksPerSec, p2ClicksPerSec, p1HitsPerSec, p2HitsPerSec, p1Pressure, p2Pressure, momentumSmooth,
                p1Conv, p2Conv, p1ReachBySec, p2ReachBySec);
        List<String> machineNotes = buildMachineNotes(record, windows, bandLabels, p1HitBands, p2HitBands, avgReach1,
                avgReach2, p1Pressure, p2Pressure, p1Conv, p2Conv, p1Path, p2Path, p1HitRate, p2HitRate, p1ClickRate,
                p2ClickRate, p1MechanicalConsistency, p2MechanicalConsistency, avgSeparation, minSeparation,
                maxSeparation, healthDeltaBySec, momentumSmooth);
        List<String> hitNotes = buildHitNotes(record, allHits, windows, avgReach1, avgReach2, sdReach1, sdReach2,
                start);
        double[] radarP1 = radarNormalize(new double[] {
                p1Hits.size(), record.getDamage1(), record.getMaxCombo1(), p1TempoPeak, avg(p1Conv) * 100, p1Path
        }, new double[] {
                p2Hits.size(), record.getDamage2(), record.getMaxCombo2(), p2TempoPeak, avg(p2Conv) * 100, p2Path
        });
        double[] radarP2 = radarNormalize(new double[] {
                p2Hits.size(), record.getDamage2(), record.getMaxCombo2(), p2TempoPeak, avg(p2Conv) * 100, p2Path
        }, new double[] {
                p1Hits.size(), record.getDamage1(), record.getMaxCombo1(), p1TempoPeak, avg(p1Conv) * 100, p1Path
        });
        html.append("<!DOCTYPE html><html lang='en'><head>");
        html.append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>VISE INTELLIGENCE REPORT ").append(escapeHtml(record.getMatchId())).append("</title>");
        html.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>");
        html.append(
                "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap' rel='stylesheet'>");
        html.append("<style>");
        html.append(
                ":root{--bg:#05070b;--bg2:#0b1020;--card:rgba(255,255,255,0.04);--card2:rgba(255,255,255,0.025);--border:rgba(255,255,255,0.085);--text:#eef4ff;--muted:#9fb0cf;--sub:#6f7f9c;--p1:#00d2ff;--p2:#ff4d8d;--accent:#8b5cf6;--good:#22c55e;--warn:#f59e0b;--bad:#ef4444;--reset:#3b4456;--trade:#f97316;}");
        html.append(
                "*{box-sizing:border-box}body{margin:0;padding:24px;font-family:Inter,sans-serif;background:radial-gradient(circle at top left,#11192f 0%,#06080d 45%,#040507 100%);color:var(--text)}");
        html.append(
                ".container{max-width:1880px;margin:0 auto}.header{display:flex;justify-content:space-between;gap:18px;align-items:flex-end;padding:10px 4px 28px 4px;border-bottom:1px solid var(--border);margin-bottom:22px}.title{font-size:42px;font-weight:800;letter-spacing:-1px;margin:0}.subtitle{color:var(--muted);font-size:13px;margin-top:8px;line-height:1.5;max-width:920px}.meta{color:var(--muted);text-align:right;font-size:13px;line-height:1.7}.grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:16px}.card{background:linear-gradient(180deg,var(--card),var(--card2));border:1px solid var(--border);border-radius:22px;padding:18px 18px 16px 18px;backdrop-filter:blur(14px);box-shadow:0 10px 25px rgba(0,0,0,0.24)}");
        html.append(
                ".span-12{grid-column:span 12}.span-8{grid-column:span 8}.span-6{grid-column:span 6}.span-4{grid-column:span 4}.span-3{grid-column:span 3}.card-title{font-size:12px;text-transform:uppercase;letter-spacing:1.3px;color:var(--muted);font-weight:700;margin-bottom:10px}.big{font-size:34px;font-weight:800;letter-spacing:-1px}.small{font-size:12px;color:var(--sub)}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric{padding:14px;border:1px solid rgba(255,255,255,0.06);border-radius:18px;background:rgba(255,255,255,0.025)}.metric .v{font-size:24px;font-weight:800;line-height:1.1}.metric .l{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:1px;margin-top:6px}.metric .s{font-size:11px;color:var(--sub);margin-top:4px}");
        html.append(
                ".chart-box{height:310px;width:100%}.chart-box.short{height:240px}.chart-box.tall{height:360px}.mono{font-family:'JetBrains Mono',monospace}.pill-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.pill{padding:8px 10px;border-radius:999px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.07);font-size:12px;color:var(--muted)}.obs{margin:0;padding-left:18px;color:var(--muted);line-height:1.65;font-size:13px}.obs li{margin:8px 0}table{width:100%;border-collapse:collapse;font-size:12px}th,td{padding:9px 8px;border-bottom:1px solid rgba(255,255,255,0.06);text-align:left}th{color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:1px}td{color:#d8e4fb}.p1{color:var(--p1)} .p2{color:var(--p2)} .accent{color:var(--accent)}");
        html.append(
                ".timeline-shell{position:relative;border:1px solid rgba(255,255,255,0.06);border-radius:18px;padding:14px 14px 18px;background:rgba(255,255,255,0.02)}.timeline-scale{position:relative;height:20px;margin:0 0 8px 72px}.timeline-tick{position:absolute;top:0;transform:translateX(-50%);font-size:10px;color:var(--sub)}.timeline-row{display:grid;grid-template-columns:62px 1fr;gap:10px;align-items:center;margin:10px 0}.timeline-label{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--muted)}.timeline-track{position:relative;height:34px;border-radius:12px;border:1px solid rgba(255,255,255,0.06);background:rgba(255,255,255,0.02);overflow:hidden}.timeline-track.hits{height:28px}.t-seg{position:absolute;top:0;bottom:0;border:0;padding:0;margin:0;cursor:pointer;opacity:.96;transition:transform .12s ease, filter .12s ease;outline:none}.t-seg:hover,.hit-marker:hover{transform:translateY(-1px);filter:brightness(1.15)}.t-seg.reset{background:linear-gradient(180deg,rgba(59,68,86,.9),rgba(59,68,86,.5))}.t-seg.neutral{background:linear-gradient(180deg,rgba(139,92,246,.84),rgba(139,92,246,.46))}.t-seg.pressure-p1,.t-seg.combo-p1,.t-seg.contact-p1{background:linear-gradient(180deg,rgba(0,210,255,.92),rgba(0,210,255,.42))}.t-seg.pressure-p2,.t-seg.combo-p2,.t-seg.contact-p2{background:linear-gradient(180deg,rgba(255,77,141,.92),rgba(255,77,141,.42))}.t-seg.trade,.t-seg.scramble{background:linear-gradient(180deg,rgba(249,115,22,.9),rgba(249,115,22,.5))}.t-seg.finish-p1{background:linear-gradient(180deg,rgba(34,197,94,.9),rgba(34,197,94,.45))}.t-seg.finish-p2{background:linear-gradient(180deg,rgba(239,68,68,.88),rgba(239,68,68,.45))}.timeline-cursor{position:absolute;top:0;bottom:0;width:2px;background:rgba(255,255,255,.88);pointer-events:none;left:0;opacity:.8}.pressure-fill{position:absolute;top:0;bottom:0;opacity:.18}.pressure-fill.p1{background:var(--p1)}.pressure-fill.p2{background:var(--p2)}.hit-marker{position:absolute;top:50%;transform:translate(-50%,-50%);width:10px;height:10px;border-radius:999px;border:1px solid rgba(255,255,255,.66);cursor:pointer;background:var(--accent)}.hit-marker.p1{background:var(--p1)}.hit-marker.p2{background:var(--p2)}.legend{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.legend .pill{padding:6px 8px}.inspector-title{font-size:20px;font-weight:800;letter-spacing:-.5px}.inspector-phase{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--muted);margin-top:6px}.inspector-body{margin-top:12px;color:#d8e4fb;font-size:13px;line-height:1.7}.inspector-meta{margin-top:14px;padding-top:12px;border-top:1px solid rgba(255,255,255,0.06);color:var(--muted);font-size:12px;line-height:1.65}.badge-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.badge{font-size:11px;padding:7px 9px;border-radius:999px;border:1px solid rgba(255,255,255,0.07);background:rgba(255,255,255,0.03);color:var(--muted)}.deep-note{padding:12px 14px;border-radius:16px;background:rgba(255,255,255,0.025);border:1px solid rgba(255,255,255,0.06);margin-top:10px}.deep-note .h{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--muted);margin-bottom:6px}.deep-note .b{color:#dce7fb;font-size:13px;line-height:1.7}.subgrid{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:1200px){.span-8,.span-6,.span-4,.span-3{grid-column:span 12}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.subgrid{grid-template-columns:1fr}}");
        html.append("</style></head><body><div class='container'>");
        html.append("<div class='header'><div><h1 class='title'>VISE INTELLIGENCE REPORT</h1>");
        html.append(
                "<div class='subtitle'>Dense ranked duel intelligence export built only from live listener telemetry: hit events, arm swings, damage totals, combo ownership, and one-second combat snapshots. The goal here is not just to show numbers, but to explain fight structure, pressure ownership, pacing, resets, and turning points.</div>");
        html.append("</div><div class='meta mono'>");
        html.append("<div>Match: ").append(escapeHtml(record.getMatchId())).append("</div>");
        html.append("<div>Kit: ").append(escapeHtml(record.getKitName())).append("</div>");
        html.append("<div>Started: ").append(sdf.format(new Date(start))).append("</div>");
        html.append("<div>Duration: ").append(DF_2.format(durationMs / 1000.0)).append("s</div>");
        html.append("<div>Snapshots: ").append(snapCount).append("</div></div></div>");
        html.append("<div class='grid'>");
        appendMetricCard(html, "Match Duration", DF_2.format(durationMs / 1000.0) + "s",
                durationSec + " one-second windows", "span-3");
        appendMetricCard(html, "Total Hits", String.valueOf(allHits.size()),
                p1Hits.size() + " / " + p2Hits.size() + " by player", "span-3");
        appendMetricCard(html, "Total Clicks", String.valueOf(clicks1.size() + clicks2.size()),
                clicks1.size() + " / " + clicks2.size(), "span-3");
        appendMetricCard(
                html, "Pressure Peak", DF_2.format(Math.max(p1TempoPeak, p2TempoPeak)), record.getPlayer1Name() + " "
                        + DF_2.format(p1TempoPeak) + " | " + record.getPlayer2Name() + " " + DF_2.format(p2TempoPeak),
                "span-3");
        html.append("<div class='card span-6' style='border-top:2px solid var(--p1)'><div class='card-title p1'>")
                .append(escapeHtml(record.getPlayer1Name())).append(" VISE Panel</div><div class='metric-grid'>");
        addMetric(html, DF_2.format(avgReach1) + "m", "Avg Reach",
                "P95 " + DF_2.format(p95Reach1) + "m, max " + DF_2.format(maxReach1) + "m");
        addMetric(html, String.valueOf(p1Hits.size()), "Hits",
                "rate " + DF_2.format(p1HitRate) + "/s, combo " + record.getMaxCombo1());
        addMetric(html, String.valueOf(clicks1.size()), "Clicks",
                "rate " + DF_2.format(p1ClickRate) + "/s, peak " + DF_1.format(p1CpsPeak));
        addMetric(html, DF_1.format(record.getDamage1()), "Damage",
                "per hit " + DF_2.format(p1DamagePerHit) + ", per click " + DF_3.format(p1DamagePerClick));
        addMetric(html, DF_2.format(avg(p1Conv) * 100) + "%", "Conversion", "rolling click->hit model");
        addMetric(html, DF_2.format(p1TempoPeak), "Pressure Peak", "tempo window peak");
        addMetric(html, DF_2.format(p1Path) + "m", "Path Length",
                "avg speed " + DF_2.format(avgSpeed1) + ", max " + DF_2.format(maxSpeed1));
        addMetric(html, DF_1.format(p1MechanicalConsistency), "Consistency",
                "pitch avg " + DF_1.format(avgPitch1) + ", y-offset " + DF_2.format(yCombat1));
        html.append("</div></div>");
        html.append("<div class='card span-6' style='border-top:2px solid var(--p2)'><div class='card-title p2'>")
                .append(escapeHtml(record.getPlayer2Name())).append(" VISE Panel</div><div class='metric-grid'>");
        addMetric(html, DF_2.format(avgReach2) + "m", "Avg Reach",
                "P95 " + DF_2.format(p95Reach2) + "m, max " + DF_2.format(maxReach2) + "m");
        addMetric(html, String.valueOf(p2Hits.size()), "Hits",
                "rate " + DF_2.format(p2HitRate) + "/s, combo " + record.getMaxCombo2());
        addMetric(html, String.valueOf(clicks2.size()), "Clicks",
                "rate " + DF_2.format(p2ClickRate) + "/s, peak " + DF_1.format(p2CpsPeak));
        addMetric(html, DF_1.format(record.getDamage2()), "Damage",
                "per hit " + DF_2.format(p2DamagePerHit) + ", per click " + DF_3.format(p2DamagePerClick));
        addMetric(html, DF_2.format(avg(p2Conv) * 100) + "%", "Conversion", "rolling click->hit model");
        addMetric(html, DF_2.format(p2TempoPeak), "Pressure Peak", "tempo window peak");
        addMetric(html, DF_2.format(p2Path) + "m", "Path Length",
                "avg speed " + DF_2.format(avgSpeed2) + ", max " + DF_2.format(maxSpeed2));
        addMetric(html, DF_1.format(p2MechanicalConsistency), "Consistency",
                "pitch avg " + DF_1.format(avgPitch2) + ", y-offset " + DF_2.format(yCombat2));
        html.append("</div></div>");
        html.append(
                "<div class='card span-8'><div class='card-title'>Master Timeline Combat Flow / Hits / Pressure</div><div class='timeline-shell'>");
        html.append("<div class='timeline-scale'>");
        int scaleMarks = Math.min(8, Math.max(2, durationSec));
        for (int i = 0; i <= scaleMarks; i++) {
            double pct = (i * 100.0) / scaleMarks;
            double sec = (durationSec * i) / (double) scaleMarks;
            html.append("<div class='timeline-tick mono' style='left:").append(DF_2.format(pct)).append("%'>")
                    .append(DF_1.format(sec)).append("s</div>");
        }
        html.append("</div>");
        html.append(
                "<div class='timeline-row'><div class='timeline-label'>Flow</div><div class='timeline-track' id='flowTrack'><div class='timeline-cursor' id='timelineCursor'></div>");
        double maxPressure = Math.max(max(p1Pressure), max(p2Pressure));
        for (int i = 0; i < windows.size(); i++) {
            WindowStats w = windows.get(i);
            double left = (i * 100.0) / windows.size();
            double width = 100.0 / windows.size();
            double p1Alpha = clamp(w.p1Pressure / Math.max(1.0, maxPressure), 0, 1);
            double p2Alpha = clamp(w.p2Pressure / Math.max(1.0, maxPressure), 0, 1);
            html.append("<div class='pressure-fill p1' style='left:").append(DF_3.format(left)).append("%;width:")
                    .append(DF_3.format(width)).append("%;opacity:").append(DF_3.format(p1Alpha * 0.25))
                    .append("'></div>");
            html.append("<div class='pressure-fill p2' style='left:").append(DF_3.format(left)).append("%;width:")
                    .append(DF_3.format(width)).append("%;opacity:").append(DF_3.format(p2Alpha * 0.25))
                    .append("'></div>");
            html.append("<button class='t-seg ").append(cssPhaseClass(w.phase)).append("' style='left:")
                    .append(DF_3.format(left)).append("%;width:").append(DF_3.format(width))
                    .append("%' onclick='inspectWindow(").append(i).append(")' title='")
                    .append(escapeHtml(w.phaseLabel)).append("'></button>");
        }
        html.append("</div></div>");
        html.append(
                "<div class='timeline-row'><div class='timeline-label'>Hits</div><div class='timeline-track hits'>");
        for (int i = 0; i < allHits.size(); i++) {
            RankedMatchRecord.HitRecord hit = allHits.get(i);
            double left = clamp(((hit.timestamp() - start) / (double) durationMs) * 100.0, 0, 100);
            boolean isP1 = hit.attacker().equals(record.getPlayer1());
            html.append("<button class='hit-marker ").append(isP1 ? "p1" : "p2").append("' style='left:")
                    .append(DF_3.format(left)).append("%' onclick='inspectHit(").append(i).append(")' title='")
                    .append(isP1 ? escapeHtml(record.getPlayer1Name()) : escapeHtml(record.getPlayer2Name()))
                    .append(" hit'></button>");
        }
        html.append("</div></div>");
        html.append(
                "<div class='timeline-row'><div class='timeline-label'>Read</div><div class='timeline-track mono' style='height:30px;padding:6px 10px;color:var(--muted);display:flex;align-items:center'>Click a flow segment to inspect a one-second combat read, or click any hit marker to inspect the exact impact note.</div></div>");
        html.append("<div class='legend'>");
        html.append("<div class='pill'>Neutral / reset</div><div class='pill'>Blue = ")
                .append(escapeHtml(record.getPlayer1Name())).append("</div><div class='pill'>Pink = ")
                .append(escapeHtml(record.getPlayer2Name()))
                .append("</div><div class='pill'>Orange = contested / scramble</div><div class='pill'>Green / red = late finish windows</div>");
        html.append("</div></div></div>");
        html.append("<div class='card span-4'><div class='card-title'>Timeline Inspector</div>");
        html.append("<div class='inspector-title' id='inspectTitle'>Awaiting Selection</div>");
        html.append("<div class='inspector-phase mono' id='inspectPhase'>Click a flow block or hit marker.</div>");
        html.append(
                "<div class='inspector-body' id='inspectBody'>The inspector translates raw listener telemetry into a local one-second duel read: pacing, pressure balance, engagement distance, conversion quality, and what the machine thinks that window meant.</div>");
        html.append("<div class='inspector-meta mono' id='inspectMeta'>No segment selected yet.</div>");
        html.append(
                "<div class='badge-row' id='inspectBadges'><div class='badge'>VISE ready</div><div class='badge'>1s windows</div><div class='badge'>click / hit / snapshot fusion</div></div></div>");
        html.append("<div class='card span-12'><div class='card-title'>VISE Machine Notes</div>");
        for (int i = 0; i < machineNotes.size(); i++) {
            html.append("<div class='deep-note'><div class='h'>Note ").append(i + 1).append("</div><div class='b'>")
                    .append(escapeHtml(machineNotes.get(i))).append("</div></div>");
        }
        html.append("<div class='pill-row mono'><div class='pill'>Health lead frames ")
                .append(escapeHtml(record.getPlayer1Name())).append(": ").append(p1LeadFrames)
                .append("</div><div class='pill'>Health lead frames ").append(escapeHtml(record.getPlayer2Name()))
                .append(": ").append(p2LeadFrames).append("</div><div class='pill'>Tied frames: ").append(tiedFrames)
                .append("</div><div class='pill'>Avg separation: ").append(DF_2.format(avgSeparation))
                .append("m</div><div class='pill'>Ping delta avg: ").append(DF_2.format(avg(pingDeltaBySec)))
                .append("ms</div></div>");
        html.append("</div>");
        appendChartCard(html, "Health Over Time", "healthChart", "span-8", "tall");
        appendChartCard(html, "Momentum Over Time", "momentumChart", "span-4", "tall");
        appendChartCard(html, "Pressure Balance", "pressureChart", "span-8", "");
        appendChartCard(html, "Rolling Conversion", "conversionChart", "span-4", "");
        appendChartCard(html, "Clicks Per Second", "cpsChart", "span-6", "");
        appendChartCard(html, "Hits Per Second", "hpsChart", "span-6", "");
        appendChartCard(html, "Separation Over Time", "separationChart", "span-6", "");
        appendChartCard(html, "Movement Speed", "speedChart", "span-6", "");
        appendChartCard(html, "Reach By Time", "reachTimeChart", "span-8", "");
        appendChartCard(html, "Engagement Bands", "reachBandChart", "span-4", "");
        appendChartCard(html, "Reach vs Vertical Offset", "verticalReachChart", "span-6", "");
        appendChartCard(html, "Ticks Between Hits", "intervalChart", "span-6", "");
        appendChartCard(html, "Impact Pitch Profile", "pitchChart", "span-6", "");
        appendChartCard(html, "Impact Yaw / Reach", "yawReachChart", "span-6", "");
        appendChartCard(html, "Arena Positional Scatter", "movementScatterChart", "span-6", "");
        appendChartCard(html, "Arena Density Map", "movementDensityChart", "span-6", "");
        appendChartCard(html, "Network Telemetry", "pingChart", "span-6", "");
        appendChartCard(html, "Distance From Opening Point", "fromStartChart", "span-6", "");
        appendChartCard(html, "Comparative Radar", "radarChart", "span-6", "");
        appendChartCard(html, "Cumulative Inputs & Hits", "cumulativeChart", "span-6", "");
        html.append(
                "<div class='card span-6'><div class='card-title'>Combat Statistics Table</div><table><thead><tr><th>Metric</th><th>")
                .append(escapeHtml(record.getPlayer1Name())).append("</th><th>")
                .append(escapeHtml(record.getPlayer2Name())).append("</th></tr></thead><tbody>");
        row(html, "Avg Reach", DF_2.format(avgReach1) + "m", DF_2.format(avgReach2) + "m");
        row(html, "Reach Std Dev", DF_3.format(sdReach1), DF_3.format(sdReach2));
        row(html, "Hit Rate", DF_2.format(p1HitRate) + "/s", DF_2.format(p2HitRate) + "/s");
        row(html, "Click Rate", DF_2.format(p1ClickRate) + "/s", DF_2.format(p2ClickRate) + "/s");
        row(html, "Damage", DF_1.format(record.getDamage1()), DF_1.format(record.getDamage2()));
        row(html, "Damage / Hit", DF_2.format(p1DamagePerHit), DF_2.format(p2DamagePerHit));
        row(html, "Avg Conversion", DF_2.format(avg(p1Conv) * 100) + "%", DF_2.format(avg(p2Conv) * 100) + "%");
        row(html, "Pressure Peak", DF_2.format(p1TempoPeak), DF_2.format(p2TempoPeak));
        row(html, "Max Combo", String.valueOf(record.getMaxCombo1()), String.valueOf(record.getMaxCombo2()));
        row(html, "Consistency", DF_1.format(p1MechanicalConsistency), DF_1.format(p2MechanicalConsistency));
        html.append("</tbody></table></div>");
        html.append(
                "<div class='card span-6'><div class='card-title'>Movement / Geometry / Network Table</div><table><thead><tr><th>Metric</th><th>")
                .append(escapeHtml(record.getPlayer1Name())).append("</th><th>")
                .append(escapeHtml(record.getPlayer2Name())).append("</th></tr></thead><tbody>");
        row(html, "Avg Speed", DF_2.format(avgSpeed1), DF_2.format(avgSpeed2));
        row(html, "Max Speed", DF_2.format(maxSpeed1), DF_2.format(maxSpeed2));
        row(html, "Path Length", DF_2.format(p1Path) + "m", DF_2.format(p2Path) + "m");
        row(html, "Avg Ping", DF_1.format(avgPing1) + "ms", DF_1.format(avgPing2) + "ms");
        row(html, "Ping Jitter", DF_2.format(jitter1), DF_2.format(jitter2));
        row(html, "Pitch Avg", DF_1.format(avgPitch1), DF_1.format(avgPitch2));
        row(html, "Pitch Spread", DF_2.format(pitchSpread1), DF_2.format(pitchSpread2));
        row(html, "Avg |Y Offset|", DF_2.format(yCombat1), DF_2.format(yCombat2));
        row(html, "Peak CPS Bucket", DF_1.format(p1CpsPeak), DF_1.format(p2CpsPeak));
        row(html, "Global Avg Separation", DF_2.format(avgSeparation) + "m", DF_2.format(avgSeparation) + "m");
        html.append("</tbody></table></div>");
        html.append("</div>");
        html.append("<script>");
        html.append(
                "const grid='rgba(255,255,255,0.07)', txt='#b8c7e6', p1='#00d2ff', p2='#ff4d8d', accent='#8b5cf6';");
        html.append(
                "function baseScales(){return{x:{grid:{color:grid},ticks:{color:txt}},y:{grid:{color:grid},ticks:{color:txt}}};}");
        html.append("function mkChart(id,cfg){new Chart(document.getElementById(id),cfg);}");
        appendWindowNotesJs(html, windows);
        appendHitNotesJs(html, hitNotes, allHits, record, start, durationMs);
        html.append(
                "function inspectWindow(i){const w=windowNotes[i];document.getElementById('inspectTitle').textContent=w.title;document.getElementById('inspectPhase').textContent=w.phase;document.getElementById('inspectBody').innerHTML=w.body;document.getElementById('inspectMeta').innerHTML=w.meta;document.getElementById('inspectBadges').innerHTML=w.badges;document.getElementById('timelineCursor').style.left=w.cursor;}");
        html.append(
                "function inspectHit(i){const h=hitNotes[i];document.getElementById('inspectTitle').textContent=h.title;document.getElementById('inspectPhase').textContent=h.phase;document.getElementById('inspectBody').innerHTML=h.body;document.getElementById('inspectMeta').innerHTML=h.meta;document.getElementById('inspectBadges').innerHTML=h.badges;document.getElementById('timelineCursor').style.left=h.cursor;}");
        html.append("if(windowNotes.length>0){inspectWindow(0);}");
        html.append("mkChart('healthChart',{type:'line',data:{labels:[").append(jsQuoted(snapLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name())).append("',data:[")
                .append(jsNumbers(p1Health))
                .append("],borderColor:p1,backgroundColor:'rgba(0,210,255,0.10)',fill:false,tension:0.25,pointRadius:0,borderWidth:2.4},")
                .append("{label:'").append(escapeJs(record.getPlayer2Name())).append("',data:[")
                .append(jsNumbers(p2Health))
                .append("],borderColor:p2,backgroundColor:'rgba(255,77,141,0.10)',fill:false,tension:0.25,pointRadius:0,borderWidth:2.4}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,interaction:{mode:'index',intersect:false},plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('momentumChart',{type:'line',data:{labels:[").append(jsQuoted(secLabels))
                .append("],datasets:[{label:'Net Momentum',data:[").append(jsNumbers(momentumSmooth))
                .append("],borderColor:accent,backgroundColor:'rgba(139,92,246,0.18)',fill:true,pointRadius:0,tension:0.25,borderWidth:2.3}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:baseScales()}});");
        html.append("mkChart('pressureChart',{type:'line',data:{labels:[").append(jsQuoted(secLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" pressure',data:[").append(jsNumbers(p1Pressure))
                .append("],borderColor:p1,pointRadius:0,tension:0.22,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" pressure',data:[").append(jsNumbers(p2Pressure))
                .append("],borderColor:p2,pointRadius:0,tension:0.22,borderWidth:2.2},")
                .append("{label:'Pressure Diff',data:[").append(jsNumbers(pressureDiff))
                .append("],borderColor:accent,pointRadius:0,tension:0.18,borderWidth:1.4,borderDash:[6,5]}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,interaction:{mode:'index',intersect:false},plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('conversionChart',{type:'line',data:{labels:[").append(jsQuoted(secLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" conversion',data:[").append(jsScaledPercent(p1Conv))
                .append("],borderColor:p1,pointRadius:0,tension:0.22,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" conversion',data:[").append(jsScaledPercent(p2Conv))
                .append("],borderColor:p2,pointRadius:0,tension:0.22,borderWidth:2.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('cpsChart',{data:{labels:[").append(jsQuoted(secLabels)).append("],datasets:[")
                .append("{type:'bar',label:'").append(escapeJs(record.getPlayer1Name())).append(" clicks',data:[")
                .append(jsInts(p1ClicksPerSec))
                .append("],backgroundColor:'rgba(0,210,255,0.28)',borderColor:p1,borderWidth:1},")
                .append("{type:'bar',label:'").append(escapeJs(record.getPlayer2Name())).append(" clicks',data:[")
                .append(jsInts(p2ClicksPerSec))
                .append("],backgroundColor:'rgba(255,77,141,0.28)',borderColor:p2,borderWidth:1},")
                .append("{type:'line',label:'").append(escapeJs(record.getPlayer1Name())).append(" smoothed',data:[")
                .append(jsNumbers(p1CpsSmooth)).append("],borderColor:p1,pointRadius:0,tension:0.25,borderWidth:2.2},")
                .append("{type:'line',label:'").append(escapeJs(record.getPlayer2Name())).append(" smoothed',data:[")
                .append(jsNumbers(p2CpsSmooth)).append("],borderColor:p2,pointRadius:0,tension:0.25,borderWidth:2.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,interaction:{mode:'index',intersect:false},plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('hpsChart',{data:{labels:[").append(jsQuoted(secLabels)).append("],datasets:[")
                .append("{type:'bar',label:'").append(escapeJs(record.getPlayer1Name())).append(" hits',data:[")
                .append(jsInts(p1HitsPerSec))
                .append("],backgroundColor:'rgba(0,210,255,0.28)',borderColor:p1,borderWidth:1},")
                .append("{type:'bar',label:'").append(escapeJs(record.getPlayer2Name())).append(" hits',data:[")
                .append(jsInts(p2HitsPerSec))
                .append("],backgroundColor:'rgba(255,77,141,0.28)',borderColor:p2,borderWidth:1},")
                .append("{type:'line',label:'").append(escapeJs(record.getPlayer1Name())).append(" flow',data:[")
                .append(jsNumbers(p1HitSmooth)).append("],borderColor:p1,pointRadius:0,tension:0.25,borderWidth:2.2},")
                .append("{type:'line',label:'").append(escapeJs(record.getPlayer2Name())).append(" flow',data:[")
                .append(jsNumbers(p2HitSmooth)).append("],borderColor:p2,pointRadius:0,tension:0.25,borderWidth:2.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,interaction:{mode:'index',intersect:false},plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('separationChart',{type:'line',data:{labels:[").append(jsQuoted(snapLabels))
                .append("],datasets:[{label:'Separation',data:[").append(jsNumbers(playerDistance))
                .append("],borderColor:accent,backgroundColor:'rgba(139,92,246,0.16)',fill:true,pointRadius:0,tension:0.2,borderWidth:2.2}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('speedChart',{type:'line',data:{labels:[").append(jsQuoted(snapLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" speed',data:[").append(jsNumbers(p1SpeedSmooth))
                .append("],borderColor:p1,pointRadius:0,tension:0.2,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" speed',data:[").append(jsNumbers(p2SpeedSmooth))
                .append("],borderColor:p2,pointRadius:0,tension:0.2,borderWidth:2.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('reachTimeChart',{type:'scatter',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" reach',data:[")
                .append(timeValuePoints(p1HitTimeSec, p1Reach))
                .append("],backgroundColor:'rgba(0,210,255,0.75)',pointRadius:3.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" reach',data:[")
                .append(timeValuePoints(p2HitTimeSec, p2Reach))
                .append("],backgroundColor:'rgba(255,77,141,0.75)',pointRadius:3.2},").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" rolling',data:[")
                .append(timeValuePoints(p1HitTimeSec, p1ReachRolling))
                .append("],showLine:true,borderColor:p1,backgroundColor:'rgba(0,0,0,0)',pointRadius:0,tension:0.2,borderWidth:1.5},")
                .append("{label:'").append(escapeJs(record.getPlayer2Name())).append(" rolling',data:[")
                .append(timeValuePoints(p2HitTimeSec, p2ReachRolling))
                .append("],showLine:true,borderColor:p2,backgroundColor:'rgba(0,0,0,0)',pointRadius:0,tension:0.2,borderWidth:1.5}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Time (s)',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Reach (m)',color:txt}}}}});");
        html.append("mkChart('reachBandChart',{type:'bar',data:{labels:[").append(jsQuoted(bandLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" hit share',data:[").append(jsInts(p1HitBands))
                .append("],backgroundColor:'rgba(0,210,255,0.42)',borderColor:p1,borderWidth:1},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" hit share',data:[").append(jsInts(p2HitBands))
                .append("],backgroundColor:'rgba(255,77,141,0.42)',borderColor:p2,borderWidth:1},")
                .append("{label:'time spent',data:[").append(jsInts(timeBands))
                .append("],backgroundColor:'rgba(139,92,246,0.22)',borderColor:accent,borderWidth:1}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('verticalReachChart',{type:'scatter',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append("',data:[").append(scatterPoints(p1YDiff, p1Reach))
                .append("],backgroundColor:'rgba(0,210,255,0.72)',pointRadius:3.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append("',data:[").append(scatterPoints(p2YDiff, p2Reach))
                .append("],backgroundColor:'rgba(255,77,141,0.72)',pointRadius:3.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Y offset at hit',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Reach (m)',color:txt}}}}});");
        html.append("mkChart('intervalChart',{type:'scatter',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" intervals',data:[")
                .append(timeValuePoints(p1HitTimeSec, p1HitIntervalsTicks))
                .append("],backgroundColor:'rgba(0,210,255,0.75)',pointRadius:3.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" intervals',data:[")
                .append(timeValuePoints(p2HitTimeSec, p2HitIntervalsTicks))
                .append("],backgroundColor:'rgba(255,77,141,0.75)',pointRadius:3.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Time (s)',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Ticks Since Previous Hit',color:txt}}}}});");
        html.append("mkChart('pitchChart',{type:'bar',data:{labels:[").append(pitchLabels).append("],datasets:[")
                .append("{label:'").append(escapeJs(record.getPlayer1Name())).append(" pitch',data:[")
                .append(jsInts(p1PitchHist))
                .append("],backgroundColor:'rgba(0,210,255,0.42)',borderColor:p1,borderWidth:1},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" pitch',data:[").append(jsInts(p2PitchHist))
                .append("],backgroundColor:'rgba(255,77,141,0.42)',borderColor:p2,borderWidth:1}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('yawReachChart',{type:'scatter',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" yaw/reach',data:[")
                .append(scatterPoints(p1Yaw, p1Reach))
                .append("],backgroundColor:'rgba(0,210,255,0.72)',pointRadius:3.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" yaw/reach',data:[")
                .append(scatterPoints(p2Yaw, p2Reach))
                .append("],backgroundColor:'rgba(255,77,141,0.72)',pointRadius:3.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Yaw at hit',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Reach (m)',color:txt}}}}});");
        html.append("mkChart('movementScatterChart',{type:'scatter',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" path',data:[").append(scatterPoints(p1X, p1Z))
                .append("],backgroundColor:'rgba(0,210,255,0.25)',pointRadius:2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" path',data:[").append(scatterPoints(p2X, p2Z))
                .append("],backgroundColor:'rgba(255,77,141,0.25)',pointRadius:2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'X',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Z',color:txt}}}}});");
        html.append("mkChart('movementDensityChart',{type:'bubble',data:{datasets:[").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" density',data:[")
                .append(densityBubblePoints(p1X, p1Z, 0.75))
                .append("],backgroundColor:'rgba(0,210,255,0.22)',borderColor:p1,borderWidth:1},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" density',data:[")
                .append(densityBubblePoints(p2X, p2Z, 0.75))
                .append("],backgroundColor:'rgba(255,77,141,0.22)',borderColor:p2,borderWidth:1}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{x:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'X',color:txt}},y:{grid:{color:grid},ticks:{color:txt},title:{display:true,text:'Z',color:txt}}}}});");
        html.append("mkChart('pingChart',{type:'line',data:{labels:[").append(jsQuoted(snapLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" ping',data:[").append(jsNumbers(p1Ping))
                .append("],borderColor:p1,pointRadius:0,tension:0.2,borderWidth:2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" ping',data:[").append(jsNumbers(p2Ping))
                .append("],borderColor:p2,pointRadius:0,tension:0.2,borderWidth:2},").append("{label:'delta',data:[")
                .append(jsNumbers(pingDelta))
                .append("],borderColor:accent,pointRadius:0,tension:0.2,borderWidth:1.6,borderDash:[6,5]}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("mkChart('fromStartChart',{type:'line',data:{labels:[").append(jsQuoted(snapLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" drift',data:[").append(jsNumbers(p1SeparationFromStart))
                .append("],borderColor:p1,pointRadius:0,tension:0.2,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" drift',data:[")
                .append(jsNumbers(p2SeparationFromStart))
                .append("],borderColor:p2,pointRadius:0,tension:0.2,borderWidth:2.2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append(
                "mkChart('radarChart',{type:'radar',data:{labels:['Hits','Damage','Combo','Pressure','Conversion','Path'],datasets:[")
                .append("{label:'").append(escapeJs(record.getPlayer1Name())).append("',data:[")
                .append(jsNumbers(radarP1))
                .append("],borderColor:p1,backgroundColor:'rgba(0,210,255,0.12)',pointBackgroundColor:p1},")
                .append("{label:'").append(escapeJs(record.getPlayer2Name())).append("',data:[")
                .append(jsNumbers(radarP2))
                .append("],borderColor:p2,backgroundColor:'rgba(255,77,141,0.12)',pointBackgroundColor:p2}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:{r:{angleLines:{color:grid},grid:{color:grid},pointLabels:{color:txt},ticks:{display:false},suggestedMin:0,suggestedMax:100}}}});");
        html.append("mkChart('cumulativeChart',{type:'line',data:{labels:[").append(jsQuoted(secLabels))
                .append("],datasets:[").append("{label:'").append(escapeJs(record.getPlayer1Name()))
                .append(" clicks',data:[").append(jsInts(p1CumClicks))
                .append("],borderColor:p1,pointRadius:0,tension:0.15,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer2Name())).append(" clicks',data:[").append(jsInts(p2CumClicks))
                .append("],borderColor:p2,pointRadius:0,tension:0.15,borderWidth:2.2},").append("{label:'")
                .append(escapeJs(record.getPlayer1Name())).append(" hits',data:[").append(jsInts(p1CumHits))
                .append("],borderColor:p1,pointRadius:0,tension:0.15,borderWidth:1.4,borderDash:[6,5]},")
                .append("{label:'").append(escapeJs(record.getPlayer2Name())).append(" hits',data:[")
                .append(jsInts(p2CumHits))
                .append("],borderColor:p2,pointRadius:0,tension:0.15,borderWidth:1.4,borderDash:[6,5]}")
                .append("]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:txt}}},scales:baseScales()}});");
        html.append("</script></body></html>");
        return html.toString();
    }

    private static List<WindowStats> buildWindows(RankedMatchRecord record, int durationSec, double[] separationBySec,
            double[] healthDeltaBySec, double[] healthSwing, int[] p1ClicksPerSec, int[] p2ClicksPerSec,
            int[] p1HitsPerSec, int[] p2HitsPerSec, double[] p1Pressure, double[] p2Pressure, double[] momentum,
            double[] p1Conv, double[] p2Conv, double[] p1ReachBySec, double[] p2ReachBySec) {
        List<WindowStats> out = new ArrayList<>();
        for (int i = 0; i <= durationSec; i++) {
            WindowStats w = new WindowStats();
            w.index = i;
            w.startSec = i;
            w.endSec = i + 1;
            w.p1Clicks = p1ClicksPerSec[i];
            w.p2Clicks = p2ClicksPerSec[i];
            w.p1Hits = p1HitsPerSec[i];
            w.p2Hits = p2HitsPerSec[i];
            w.p1Pressure = p1Pressure[i];
            w.p2Pressure = p2Pressure[i];
            w.momentum = momentum[i];
            w.separation = separationBySec[i];
            w.healthDelta = healthDeltaBySec[i];
            w.healthSwing = healthSwing[i];
            w.p1Conversion = p1Conv[i];
            w.p2Conversion = p2Conv[i];
            w.p1Reach = p1ReachBySec[i];
            w.p2Reach = p2ReachBySec[i];
            classifyWindow(w, durationSec);
            buildWindowNarrative(w, record);
            out.add(w);
        }
        return out;
    }

    private static void classifyWindow(WindowStats w, int durationSec) {
        int totalHits = w.p1Hits + w.p2Hits;
        int totalClicks = w.p1Clicks + w.p2Clicks;
        boolean late = w.index >= Math.max(0, durationSec - 2);
        double diff = w.p1Pressure - w.p2Pressure;
        if (late && totalHits > 0) {
            w.phase = diff >= 0 ? "FINISH_P1" : "FINISH_P2";
            w.phaseLabel = diff >= 0 ? "Late closing control: P1" : "Late closing control: P2";
            return;
        }
        if (totalHits == 0 && totalClicks <= 1 && w.separation >= 3.6) {
            w.phase = "RESET";
            w.phaseLabel = "Reset / space rebuild";
        } else if (totalHits == 0 && totalClicks <= 6) {
            if (w.separation <= 2.2) {
                w.phase = "SCRAMBLE";
                w.phaseLabel = "Close scramble / no clean confirmation";
            } else {
                w.phase = "NEUTRAL";
                w.phaseLabel = "Neutral spacing window";
            }
        } else if (w.p1Hits > 0 && w.p2Hits > 0) {
            w.phase = "TRADE";
            w.phaseLabel = "Contested trade window";
        } else if (w.p1Hits >= 2 || (w.p1Hits > 0 && diff > 2.0)) {
            w.phase = "COMBO_P1";
            w.phaseLabel = "P1 sustained control";
        } else if (w.p2Hits >= 2 || (w.p2Hits > 0 && diff < -2.0)) {
            w.phase = "COMBO_P2";
            w.phaseLabel = "P2 sustained control";
        } else if (diff > 1.4) {
            w.phase = w.p1Hits > 0 ? "CONTACT_P1" : "PRESSURE_P1";
            w.phaseLabel = w.p1Hits > 0 ? "P1 clean contact" : "P1 pressure build";
        } else if (diff < -1.4) {
            w.phase = w.p2Hits > 0 ? "CONTACT_P2" : "PRESSURE_P2";
            w.phaseLabel = w.p2Hits > 0 ? "P2 clean contact" : "P2 pressure build";
        } else {
            w.phase = "NEUTRAL";
            w.phaseLabel = "Balanced flow window";
        }
    }

    private static void buildWindowNarrative(WindowStats w, RankedMatchRecord record) {
        String p1 = record.getPlayer1Name();
        String p2 = record.getPlayer2Name();
        String dominant = w.p1Pressure > w.p2Pressure ? p1 : (w.p2Pressure > w.p1Pressure ? p2 : "Neither side");
        StringBuilder body = new StringBuilder();
        body.append("<div><strong>").append(escapeHtml(windowHeadline(w, p1, p2))).append("</strong></div>");
        body.append("<div style='margin-top:8px'>");
        body.append("This one-second read logged <strong>").append(w.p1Hits).append("</strong> / <strong>")
                .append(w.p2Hits).append("</strong> hits and <strong>").append(w.p1Clicks)
                .append("</strong> / <strong>").append(w.p2Clicks)
                .append("</strong> clicks. Average separation sat at <strong>").append(DF_2.format(w.separation))
                .append("m</strong> while health delta closed the window at <strong>")
                .append(DF_2.format(w.healthDelta)).append("</strong>.");
        body.append("</div>");
        body.append("<ul class='obs' style='margin-top:10px'>");
        body.append("<li>Pressure read: ").append(escapeHtml(p1)).append(" ").append(DF_2.format(w.p1Pressure))
                .append(" vs ").append(escapeHtml(p2)).append(" ").append(DF_2.format(w.p2Pressure)).append(".</li>");
        body.append("<li>Conversion read: ").append(escapeHtml(p1)).append(" ")
                .append(DF_1.format(w.p1Conversion * 100)).append("% vs ").append(escapeHtml(p2)).append(" ")
                .append(DF_1.format(w.p2Conversion * 100)).append("%.</li>");
        body.append("<li>Reach in this window: ").append(escapeHtml(p1)).append(" ").append(DF_2.format(w.p1Reach))
                .append("m, ").append(escapeHtml(p2)).append(" ").append(DF_2.format(w.p2Reach)).append("m.</li>");
        body.append("<li>Machine take: ").append(escapeHtml(machineInterpretation(w, p1, p2, dominant)))
                .append("</li>");
        body.append("</ul>");
        w.bodyHtml = body.toString();
        w.metaHtml = "Window " + w.index + "s to " + (w.index + 1) + "s | Phase: " + w.phaseLabel + " | Momentum "
                + DF_2.format(w.momentum);
        w.badgesHtml = "<div class='badge'>" + escapeHtml(w.phaseLabel) + "</div>" + "<div class='badge'>sep "
                + DF_2.format(w.separation) + "m</div>" + "<div class='badge'>swing "
                + signed(DF_2.format(w.healthSwing)) + "</div>" + "<div class='badge'>pressure delta "
                + signed(DF_2.format(w.p1Pressure - w.p2Pressure)) + "</div>";
        w.title = "VISE window " + w.index + "s";
    }

    private static String windowHeadline(WindowStats w, String p1, String p2) {
        return switch (w.phase) {
            case "RESET" -> "Both players pulled the pace down and rebuilt space.";
            case "NEUTRAL" -> "Neither side secured a clean confirmation; this was spacing and positioning.";
            case "TRADE" -> "Both sides touched the exchange, so follow-up value mattered more than first contact.";
            case "SCRAMBLE" -> "The players were close enough for chaos, but the window did not resolve cleanly.";
            case "COMBO_P1" -> p1 + " owned a true sustained window here.";
            case "COMBO_P2" -> p2 + " owned a true sustained window here.";
            case "PRESSURE_P1", "CONTACT_P1" -> p1 + " was pushing the structure of the duel forward.";
            case "PRESSURE_P2", "CONTACT_P2" -> p2 + " was pushing the structure of the duel forward.";
            case "FINISH_P1" -> p1 + " controlled a late closing window.";
            case "FINISH_P2" -> p2 + " controlled a late closing window.";
            default -> "This was a balanced but still meaningful slice of the fight.";
        };
    }

    private static String machineInterpretation(WindowStats w, String p1, String p2, String dominant) {
        if ("RESET".equals(w.phase)) {
            return "The duel stepped out of active trading and prioritized spacing recovery over immediate payoff.";
        }
        if ("NEUTRAL".equals(w.phase)) {
            return "Inputs were present, but the window was more about position and initiative than actual damage conversion.";
        }
        if ("TRADE".equals(w.phase)) {
            return "Both sides interacted, so the decisive factor was who stabilized after contact rather than who clicked first.";
        }
        if ("SCRAMBLE".equals(w.phase)) {
            return "Close spacing inflated tension, but the exchange remained unstable and under-resolved.";
        }
        if (w.phase.startsWith("COMBO_")) {
            return dominant + " translated presence into sustained ownership rather than a single isolated tag.";
        }
        if (w.phase.startsWith("PRESSURE_") || w.phase.startsWith("CONTACT_")) {
            if (Math.max(w.p1Conversion, w.p2Conversion) < 0.12) {
                return dominant + " generated pace, but the window still had weak final conversion behind the volume.";
            }
            return dominant + " combined pace and confirmation well enough to bend the duel state in one direction.";
        }
        if (w.phase.startsWith("FINISH_")) {
            return "The match was no longer being sculpted slowly here; this was closing pressure with real end-state weight.";
        }
        return "This window contributed structure without clearly dominating the duel on its own.";
    }

    private static List<String> buildMachineNotes(RankedMatchRecord record, List<WindowStats> windows,
            String[] bandLabels, int[] p1HitBands, int[] p2HitBands, double avgReach1, double avgReach2,
            double[] p1Pressure, double[] p2Pressure, double[] p1Conv, double[] p2Conv, double p1Path, double p2Path,
            double p1HitRate, double p2HitRate, double p1ClickRate, double p2ClickRate, double p1MechanicalConsistency,
            double p2MechanicalConsistency, double avgSeparation, double minSeparation, double maxSeparation,
            double[] healthDeltaBySec, double[] momentum) {
        List<String> notes = new ArrayList<>();
        String p1 = record.getPlayer1Name();
        String p2 = record.getPlayer2Name();
        WindowStats turning = windows.stream().max(Comparator.comparingDouble(w -> Math.abs(w.momentum))).orElse(null);
        WindowStats opener = windows.stream().filter(w -> w.p1Hits + w.p2Hits > 0).findFirst().orElse(null);
        if (opener != null) {
            String openerOwner = opener.p1Hits > opener.p2Hits ? p1
                    : (opener.p2Hits > opener.p1Hits ? p2 : "Neither side");
            notes.add("The first meaningful contact belonged to " + openerOwner
                    + ". That matters because the opening window usually reveals who established the duel's first real shape instead of just its first animation.");
        } else {
            notes.add(
                    "The fight opened slowly enough that the first clean control window arrived late. That usually means the duel was decided more by mid-fight structure than by instant first-contact snowball.");
        }
        if (avgReach1 > avgReach2 + 0.10) {
            notes.add(p1
                    + " fought from the longer effective range overall. The gap was not just visual; the hit profile suggests that "
                    + p1 + " got value from a slightly wider engagement band.");
        } else if (avgReach2 > avgReach1 + 0.10) {
            notes.add(p2
                    + " fought from the longer effective range overall. That points to more value extracted from outer-band contact rather than purely close scramble success.");
        } else {
            notes.add(
                    "Both players lived in nearly the same range bands. This duel was decided more by pressure quality and conversion than by radically different spacing philosophies.");
        }
        String p1Band = dominantBandLabel(bandLabels, p1HitBands);
        String p2Band = dominantBandLabel(bandLabels, p2HitBands);
        notes.add("Engagement band read: " + p1 + " was most productive in the " + p1Band + " range, while " + p2
                + " got most of their confirmed value in the " + p2Band
                + " range. That gives the report an actual geometry story instead of a single average reach number.");
        double p1PressureLead = avg(subtract(p1Pressure, p2Pressure));
        if (p1PressureLead > 0.45) {
            notes.add(p1
                    + " owned more of the match's pressure shape. That does not just mean more hits; it means more windows where clicks, confirmations, and health swing were aligned in the same direction.");
        } else if (p1PressureLead < -0.45) {
            notes.add(p2 + " owned more of the match's pressure shape. The duel's internal rhythm leaned toward " + p2
                    + " even before looking only at the final totals.");
        } else {
            notes.add(
                    "Pressure was relatively balanced. The duel did not belong to one player at every moment, so the biggest difference likely came from the quality of the decisive windows rather than constant control.");
        }
        if (avg(p1Conv) > avg(p2Conv) + 0.05) {
            notes.add(p1
                    + " converted input volume into real contact more efficiently. In plain terms, the clicks were more often connected to confirmed pressure instead of empty tempo.");
        } else if (avg(p2Conv) > avg(p1Conv) + 0.05) {
            notes.add(p2
                    + " converted input volume into real contact more efficiently. That usually means their pace had cleaner payoff behind it.");
        } else {
            notes.add(
                    "Click-to-hit conversion was close. The duel was not decided by one player wildly out-performing the other on raw input efficiency alone.");
        }
        if (p1Path > p2Path * 1.12) {
            notes.add(p1
                    + " covered much more ground. That suggests the match leaned more on repositioning and chase geometry for "
                    + p1 + " than on planted close-range persistence.");
        } else if (p2Path > p1Path * 1.12) {
            notes.add(p2 + " covered much more ground. That gives the fight a movement-economy angle: " + p2
                    + " spent more of the match solving space before cashing it in.");
        } else {
            notes.add(
                    "Movement volume was similar on both sides. That keeps the emphasis on exchange quality instead of one player simply outrunning the duel structure.");
        }
        if (p1MechanicalConsistency > p2MechanicalConsistency + 4) {
            notes.add(p1
                    + " showed the steadier mechanical profile in this sample. The combination of reach spread, hit interval stability, and network cleanliness gave the machine a stronger consistency read.");
        } else if (p2MechanicalConsistency > p1MechanicalConsistency + 4) {
            notes.add(p2
                    + " showed the steadier mechanical profile in this sample. Their duel signature looked more settled and repeatable across the match.");
        } else {
            notes.add(
                    "Mechanical regularity was close between the two players. That makes the report read more like a battle of decisions and timing than one of wildly different execution stability.");
        }
        if (turning != null) {
            String owner = turning.momentum >= 0 ? p1 : p2;
            notes.add("The sharpest turning point appeared in the " + turning.index
                    + "s window, where the momentum model swung hardest toward " + owner
                    + ". That window is the best one to inspect first if you want the duel's decisive structural shift.");
        }
        double finalDelta = healthDeltaBySec.length == 0 ? 0 : healthDeltaBySec[healthDeltaBySec.length - 1];
        if (finalDelta > 0) {
            notes.add("Late state favored " + p1
                    + ". Even if pressure was shared earlier, the duel closed with the health story leaning their way.");
        } else if (finalDelta < 0) {
            notes.add("Late state favored " + p2
                    + ". The closer mattered, and the closing structure held for them when the fight was compressing.");
        } else {
            notes.add(
                    "The end-state health picture stayed very close. This was not a lazy snowball; it was a duel that kept some tension even late.");
        }
        notes.add("Global spacing context: the duel averaged " + DF_2.format(avgSeparation)
                + "m of separation, dipping to " + DF_2.format(minSeparation) + "m at its tightest and stretching to "
                + DF_2.format(maxSeparation)
                + "m at its loosest. That range matters because the report's pressure and momentum reads are always anchored to how compressed or open the fight really was.");
        return notes;
    }

    private static List<String> buildHitNotes(RankedMatchRecord record, List<RankedMatchRecord.HitRecord> allHits,
            List<WindowStats> windows, double avgReach1, double avgReach2, double sdReach1, double sdReach2,
            long start) {
        List<String> out = new ArrayList<>();
        Map<UUID, Long> lastHitByAttacker = new HashMap<>();
        for (RankedMatchRecord.HitRecord hit : allHits) {
            boolean isP1 = hit.attacker().equals(record.getPlayer1());
            String attacker = isP1 ? record.getPlayer1Name() : record.getPlayer2Name();
            double avg = isP1 ? avgReach1 : avgReach2;
            double sd = isP1 ? sdReach1 : sdReach2;
            int sec = (int) Math.floor((hit.timestamp() - start) / 1000.0);
            sec = Math.max(0, Math.min(sec, windows.size() - 1));
            WindowStats window = windows.get(sec);
            Long last = lastHitByAttacker.put(hit.attacker(), hit.timestamp());
            double intervalTicks = last == null ? (hit.timestamp() - start) / 50.0 : (hit.timestamp() - last) / 50.0;
            String reachClass = hit.distance() > avg + Math.max(0.10, sd * 0.9) ? "outer-band tag"
                    : (hit.distance() < avg - Math.max(0.10, sd * 0.7) ? "inside-band confirmation"
                            : "normal-band contact");
            String yClass = Math.abs(hit.yDiff()) > 0.65 ? "vertical-offset contact" : "mostly flat contact";
            String aimClass = Math.abs(hit.pitch()) > 28 ? "aggressive pitch correction" : "stable pitch profile";
            String note = attacker + " landed a hit at " + DF_2.format((hit.timestamp() - start) / 1000.0) + "s from "
                    + DF_2.format(hit.distance()) + "m. " + "The machine tags it as an " + reachClass + " with "
                    + yClass + " and a " + aimClass + ". " + "Local fight context: " + window.phaseLabel + ", pressure "
                    + DF_2.format(window.p1Pressure) + " / " + DF_2.format(window.p2Pressure)
                    + ", interval since this player's last hit " + DF_2.format(intervalTicks) + " ticks.";
            out.add(note);
        }
        return out;
    }

    private static void appendWindowNotesJs(StringBuilder html, List<WindowStats> windows) {
        html.append("const windowNotes=[");
        for (int i = 0; i < windows.size(); i++) {
            WindowStats w = windows.get(i);
            if (i > 0) {
                html.append(",");
            }
            double cursor = ((i + 0.5) * 100.0) / windows.size();
            html.append("{title:'").append(escapeJs(w.title)).append("',phase:'").append(escapeJs(w.phaseLabel))
                    .append("',body:'").append(escapeJs(w.bodyHtml)).append("',meta:'").append(escapeJs(w.metaHtml))
                    .append("',badges:'").append(escapeJs(w.badgesHtml)).append("',cursor:'")
                    .append(DF_2.format(cursor)).append("%'}");
        }
        html.append("];");
    }

    private static void appendHitNotesJs(StringBuilder html, List<String> hitNotes,
            List<RankedMatchRecord.HitRecord> allHits, RankedMatchRecord record, long start, long durationMs) {
        html.append("const hitNotes=[");
        for (int i = 0; i < hitNotes.size(); i++) {
            RankedMatchRecord.HitRecord hit = allHits.get(i);
            boolean isP1 = hit.attacker().equals(record.getPlayer1());
            double cursor = clamp(((hit.timestamp() - start) / (double) durationMs) * 100.0, 0, 100);
            if (i > 0) {
                html.append(",");
            }
            html.append("{title:'")
                    .append(escapeJs((isP1 ? record.getPlayer1Name() : record.getPlayer2Name()) + " impact note"))
                    .append("',phase:'")
                    .append(escapeJs("Hit @ " + DF_2.format((hit.timestamp() - start) / 1000.0) + "s"))
                    .append("',body:'").append(escapeJs(hitNotes.get(i))).append("',meta:'")
                    .append(escapeJs("Reach "
                            + DF_2.format(hit.distance()) + "m | Yaw " + DF_1.format(normalizeYaw(hit.yaw()))
                            + " | Pitch " + DF_1.format(hit.pitch()) + " | Y offset " + DF_2.format(hit.yDiff())))
                    .append("',badges:'")
                    .append(escapeJs("<div class='badge'>"
                            + (isP1 ? escapeHtml(record.getPlayer1Name()) : escapeHtml(record.getPlayer2Name()))
                            + " hit</div><div class='badge'>reach " + DF_2.format(hit.distance())
                            + "m</div><div class='badge'>pitch " + DF_1.format(hit.pitch()) + "</div>"))
                    .append("',cursor:'").append(DF_2.format(cursor)).append("%'}");
        }
        html.append("];");
    }

    private static String cssPhaseClass(String phase) {
        return switch (phase) {
            case "RESET" -> "reset";
            case "NEUTRAL" -> "neutral";
            case "TRADE" -> "trade";
            case "SCRAMBLE" -> "scramble";
            case "PRESSURE_P1" -> "pressure-p1";
            case "PRESSURE_P2" -> "pressure-p2";
            case "CONTACT_P1" -> "contact-p1";
            case "CONTACT_P2" -> "contact-p2";
            case "COMBO_P1" -> "combo-p1";
            case "COMBO_P2" -> "combo-p2";
            case "FINISH_P1" -> "finish-p1";
            case "FINISH_P2" -> "finish-p2";
            default -> "neutral";
        };
    }

    private static String dominantBandLabel(String[] labels, int[] counts) {
        int idx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[idx]) {
                idx = i;
            }
        }
        return labels.length == 0 ? "unknown" : labels[Math.min(idx, labels.length - 1)];
    }

    private static String[] bandLabels(double[] edges) {
        String[] out = new String[Math.max(0, edges.length - 1)];
        for (int i = 0; i < out.length; i++) {
            out[i] = DF_2.format(edges[i]) + " to " + DF_2.format(edges[i + 1]);
        }
        return out;
    }

    private static int[] histogramByEdges(double[] values, double[] edges) {
        int[] out = new int[Math.max(0, edges.length - 1)];
        for (double v : values) {
            int idx = out.length - 1;
            for (int i = 0; i < edges.length - 1; i++) {
                if (v >= edges[i] && v < edges[i + 1]) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0 && idx < out.length) {
                out[idx]++;
            }
        }
        return out;
    }

    private static double[] averageHitMetricBySecond(List<RankedMatchRecord.HitRecord> hits, long start,
            int durationSec, ToDoubleFunction<RankedMatchRecord.HitRecord> value) {
        double[] sums = new double[durationSec + 1];
        int[] counts = new int[durationSec + 1];
        for (RankedMatchRecord.HitRecord hit : hits) {
            int bucket = (int) Math.floor((hit.timestamp() - start) / 1000.0);
            bucket = Math.max(0, Math.min(bucket, durationSec));
            sums[bucket] += value.applyAsDouble(hit);
            counts[bucket]++;
        }
        double[] out = new double[durationSec + 1];
        for (int i = 0; i <= durationSec; i++) {
            out[i] = counts[i] == 0 ? 0 : sums[i] / counts[i];
        }
        return out;
    }

    private static double[] valueBySecond(long[] times, double[] values, long start, int durationSec, double fallback) {
        double[] out = new double[durationSec + 1];
        if (times.length == 0 || values.length == 0) {
            Arrays.fill(out, fallback);
            return out;
        }
        int ptr = 0;
        double last = fallback;
        for (int sec = 0; sec <= durationSec; sec++) {
            long boundary = start + (sec * 1000L);
            while (ptr < times.length && times[ptr] <= boundary) {
                last = values[ptr];
                ptr++;
            }
            out[sec] = last;
        }
        if (out[0] == fallback && values.length > 0) {
            out[0] = values[0];
        }
        return out;
    }

    private static double[] rollingRatio(int[] num, int[] den, int window) {
        double[] out = new double[num.length];
        int w = Math.max(1, window);
        int numSum = 0;
        int denSum = 0;
        for (int i = 0; i < num.length; i++) {
            numSum += num[i];
            denSum += den[i];
            if (i >= w) {
                numSum -= num[i - w];
                denSum -= den[i - w];
            }
            out[i] = denSum <= 0 ? 0 : ((double) numSum / denSum);
        }
        return out;
    }

    private static double[] differenceSeries(double[] arr) {
        double[] out = new double[arr.length];
        for (int i = 1; i < arr.length; i++) {
            out[i] = arr[i] - arr[i - 1];
        }
        return out;
    }

    private static double[] subtract(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = a[i] - b[i];
        }
        return out;
    }

    private static void appendMetricCard(StringBuilder html, String label, String value, String sub, String span) {
        html.append("<div class='card ").append(span).append("'><div class='card-title'>").append(escapeHtml(label))
                .append("</div><div class='big'>").append(escapeHtml(value)).append("</div><div class='small'>")
                .append(escapeHtml(sub)).append("</div></div>");
    }

    private static void addMetric(StringBuilder html, String value, String label, String sub) {
        html.append("<div class='metric'><div class='v'>").append(escapeHtml(value)).append("</div><div class='l'>")
                .append(escapeHtml(label)).append("</div><div class='s'>").append(escapeHtml(sub))
                .append("</div></div>");
    }

    private static void appendChartCard(StringBuilder html, String title, String canvasId, String span,
            String extraClass) {
        html.append("<div class='card ").append(span).append("'><div class='card-title'>").append(escapeHtml(title))
                .append("</div><div class='chart-box ").append(extraClass == null ? "" : extraClass)
                .append("'><canvas id='").append(canvasId).append("'></canvas></div></div>");
    }

    private static void row(StringBuilder html, String metric, String a, String b) {
        html.append("<tr><td>").append(escapeHtml(metric)).append("</td><td>").append(escapeHtml(a)).append("</td><td>")
                .append(escapeHtml(b)).append("</td></tr>");
    }

    private static int[] bucketCounts(List<Long> times, long start, int durationSec) {
        int[] out = new int[durationSec + 1];
        for (long t : times) {
            int bucket = (int) Math.floor((t - start) / 1000.0);
            if (bucket < 0) {
                bucket = 0;
            }
            if (bucket >= out.length) {
                bucket = out.length - 1;
            }
            out[bucket]++;
        }
        return out;
    }

    private static int[] cumulative(int[] arr) {
        int[] out = new int[arr.length];
        int running = 0;
        for (int i = 0; i < arr.length; i++) {
            running += arr[i];
            out[i] = running;
        }
        return out;
    }

    private static double[] speedSeries(double[] xs, double[] zs, long[] ts) {
        if (xs.length == 0) {
            return new double[0];
        }
        double[] out = new double[xs.length];
        out[0] = 0;
        for (int i = 1; i < xs.length; i++) {
            double dist = hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            double dt = Math.max(0.001, (ts[i] - ts[i - 1]) / 1000.0);
            out[i] = dist / dt;
        }
        return out;
    }

    private static double[] hitIntervalsTicks(List<RankedMatchRecord.HitRecord> hits, long start) {
        double[] out = new double[hits.size()];
        long last = start;
        for (int i = 0; i < hits.size(); i++) {
            long now = hits.get(i).timestamp();
            out[i] = (now - last) / 50.0;
            last = now;
        }
        return out;
    }

    private static int[] histogram(double[] values, double min, double max, double step) {
        int bins = Math.max(1, (int) Math.ceil((max - min) / step));
        int[] out = new int[bins];
        for (double v : values) {
            int idx = (int) Math.floor((v - min) / step);
            if (idx < 0) {
                idx = 0;
            }
            if (idx >= bins) {
                idx = bins - 1;
            }
            out[idx]++;
        }
        return out;
    }

    private static String[] binLabels(double min, double max, double step) {
        int bins = Math.max(1, (int) Math.ceil((max - min) / step));
        String[] labels = new String[bins];
        for (int i = 0; i < bins; i++) {
            double a = min + (i * step);
            double b = Math.min(max, a + step);
            labels[i] = DF_0.format(a) + " to " + DF_0.format(b);
        }
        return labels;
    }

    private static String scatterPoints(double[] xs, double[] ys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(xs.length, ys.length); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{x:").append(DF_3.format(xs[i])).append(",y:").append(DF_3.format(ys[i])).append("}");
        }
        return sb.toString();
    }

    private static String timeValuePoints(double[] times, double[] values) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(times.length, values.length);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{x:").append(DF_3.format(times[i])).append(",y:").append(DF_3.format(values[i])).append("}");
        }
        return sb.toString();
    }

    private static String densityBubblePoints(double[] xs, double[] ys, double cell) {
        Map<String, Integer> density = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(xs.length, ys.length); i++) {
            double gx = Math.round(xs[i] / cell) * cell;
            double gy = Math.round(ys[i] / cell) * cell;
            String key = DF_2.format(gx) + "|" + DF_2.format(gy);
            density.put(key, density.getOrDefault(key, 0) + 1);
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (Map.Entry<String, Integer> e : density.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double r = Math.min(18, 3 + (e.getValue() * 0.75));
            if (idx++ > 0) {
                sb.append(",");
            }
            sb.append("{x:").append(DF_3.format(x)).append(",y:").append(DF_3.format(y)).append(",r:")
                    .append(DF_2.format(r)).append("}");
        }
        return sb.toString();
    }

    private static double[] radarNormalize(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double max = Math.max(1.0, Math.max(a[i], b[i]));
            out[i] = (a[i] / max) * 100.0;
        }
        return out;
    }

    private static double computeConsistency(double avgReach, double reachSd, double[] intervalsTicks, double jitter) {
        double intervalSd = stdDev(intervalsTicks);
        double reachTightness = 35.0 / (1.0 + Math.max(0.01, reachSd * 8.0));
        double intervalTightness = 35.0 / (1.0 + Math.max(0.01, intervalSd / 3.0));
        double reachComponent = Math.min(15.0, avgReach * 4.5);
        double netPenalty = Math.min(20.0, jitter / 4.0);
        double score = reachTightness + intervalTightness + reachComponent - netPenalty + 20.0;
        return clamp(score, 0, 100);
    }

    private static int countGreaterThan(double[] arr, double threshold) {
        int c = 0;
        for (double v : arr) {
            if (v > threshold) {
                c++;
            }
        }
        return c;
    }

    private static int countLessThan(double[] arr, double threshold) {
        int c = 0;
        for (double v : arr) {
            if (v < threshold) {
                c++;
            }
        }
        return c;
    }

    private static double pathLength(double[] xs, double[] zs) {
        if (xs.length < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 1; i < xs.length; i++) {
            sum += hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
        }
        return sum;
    }

    private static double hypot(double x, double y) {
        return Math.sqrt((x * x) + (y * y));
    }

    private static double[] rollingAverage(double[] arr, int window) {
        if (arr.length == 0) {
            return new double[0];
        }
        double[] out = new double[arr.length];
        double sum = 0;
        int w = Math.max(1, window);
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
            if (i >= w) {
                sum -= arr[i - w];
            }
            int denom = Math.min(i + 1, w);
            out[i] = sum / denom;
        }
        return out;
    }

    private static double[] toDoubleArray(int[] arr) {
        double[] out = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    private static double avg(double[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        double s = 0;
        for (double v : arr) {
            s += v;
        }
        return s / arr.length;
    }

    private static double avgAbs(double[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        double s = 0;
        for (double v : arr) {
            s += Math.abs(v);
        }
        return s / arr.length;
    }

    private static double min(double[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        double m = Double.POSITIVE_INFINITY;
        for (double v : arr) {
            m = Math.min(m, v);
        }
        return m == Double.POSITIVE_INFINITY ? 0 : m;
    }

    private static double max(double[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        double m = Double.NEGATIVE_INFINITY;
        for (double v : arr) {
            m = Math.max(m, v);
        }
        return m == Double.NEGATIVE_INFINITY ? 0 : m;
    }

    private static double stdDev(double[] arr) {
        if (arr.length < 2) {
            return 0;
        }
        double mean = avg(arr);
        double sum = 0;
        for (double v : arr) {
            double d = v - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / arr.length);
    }

    private static double percentile(double[] arr, double pct) {
        if (arr.length == 0) {
            return 0;
        }
        double[] copy = Arrays.copyOf(arr, arr.length);
        Arrays.sort(copy);
        double pos = (pct / 100.0) * (copy.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return copy[lo];
        }
        double weight = pos - lo;
        return copy[lo] + ((copy[hi] - copy[lo]) * weight);
    }

    private static double clamp(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    private static double normalizeYaw(double yaw) {
        while (yaw > 180) {
            yaw -= 360;
        }
        while (yaw < -180) {
            yaw += 360;
        }
        return yaw;
    }

    private static String jsNumbers(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(DF_3.format(arr[i]));
        }
        return sb.toString();
    }

    private static String jsScaledPercent(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(DF_3.format(arr[i] * 100.0));
        }
        return sb.toString();
    }

    private static String jsInts(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String jsQuoted(String[] arr) {
        return Arrays.stream(arr).map(s -> "'" + escapeJs(s) + "'").collect(Collectors.joining(","));
    }

    private static String[] secondLabels(int durationSec) {
        String[] out = new String[durationSec + 1];
        for (int i = 0; i <= durationSec; i++) {
            out[i] = i + "s";
        }
        return out;
    }

    private static String[] snapTimeLabels(double[] times) {
        String[] out = new String[times.length];
        for (int i = 0; i < times.length; i++) {
            out[i] = DF_1.format(times[i]) + "s";
        }
        return out;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&#39;");
    }

    private static String escapeJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String signed(String s) {
        if (s == null || s.isEmpty() || s.startsWith("-")) {
            return s;
        }
        return "+" + s;
    }

    private static class WindowStats {
        int index;
        int startSec;
        int endSec;
        int p1Clicks;
        int p2Clicks;
        int p1Hits;
        int p2Hits;
        double p1Pressure;
        double p2Pressure;
        double momentum;
        double separation;
        double healthDelta;
        double healthSwing;
        double p1Conversion;
        double p2Conversion;
        double p1Reach;
        double p2Reach;
        String phase;
        String phaseLabel;
        String title;
        String bodyHtml;
        String metaHtml;
        String badgesHtml;
    }
}