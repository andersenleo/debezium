/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.pipeline.metrics;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.debezium.connector.common.CdcSourceTaskContext;
import io.debezium.pipeline.source.spi.SnapshotProgressListener;
import io.debezium.relational.TableId;

/**
 * @author Randall Hauch, Jiri Pechanec
 *
 */
public class SnapshotChangeEventSourceMetrics extends Metrics implements SnapshotChangeEventSourceMetricsMXBean, SnapshotProgressListener {

    private final AtomicLong tableCount = new AtomicLong();
    private final AtomicBoolean snapshotRunning = new AtomicBoolean();
    private final AtomicBoolean snapshotCompleted = new AtomicBoolean();
    private final AtomicBoolean snapshotAborted = new AtomicBoolean();
    private final AtomicLong startTime = new AtomicLong();
    private final AtomicLong stopTime = new AtomicLong();
    private final ConcurrentMap<String, Long> rowsScanned = new ConcurrentHashMap<String, Long>();
    private final ConcurrentMap<String, String> remainingTables = new ConcurrentHashMap<>();
    private Set<String> monitoredTables = new HashSet<>();

    public <T extends CdcSourceTaskContext> SnapshotChangeEventSourceMetrics(T taskContext) {
        super(taskContext, "snapshot");
    }

    @Override
    public int getTotalTableCount() {
        return this.tableCount.intValue();
    }

    @Override
    public int getRemainingTableCount() {
        return this.remainingTables.size();
    }

    @Override
    public boolean getSnapshotRunning() {
        return this.snapshotRunning.get();
    }

    @Override
    public boolean getSnapshotCompleted() {
        return this.snapshotCompleted.get();
    }

    @Override
    public boolean getSnapshotAborted() {
        return this.snapshotAborted.get();
    }

    @Override
    public long getSnapshotDurationInSeconds() {
        final long startMillis = startTime.get();
        if (startMillis <= 0L) {
            return 0;
        }
        long stopMillis = stopTime.get();
        if (stopMillis == 0L) {
            stopMillis = clock.currentTimeInMillis();
        }
        return (stopMillis - startMillis) / 1000L;
    }

    @Override
    public String[] getMonitoredTables() {
        return monitoredTables.toArray(new String[monitoredTables.size()]);
    }

    @Override
    public void setMonitoredTables(Set<TableId> tableIds) {
        this.tableCount.set(tableIds.size());
        tableIds.stream().forEach(x -> {
            this.remainingTables.put(x.toString(), "");
            monitoredTables.add(x.toString());
        });
    }

    @Override
    public void completeTable(TableId tableId, long numRows) {
        rowsScanned.put(tableId.toString(), numRows);
        this.remainingTables.remove(tableId.toString());
    }

    @Override
    public void startSnapshot() {
        this.snapshotRunning.set(true);
        this.snapshotCompleted.set(false);
        this.snapshotAborted.set(false);
        this.startTime.set(clock.currentTimeInMillis());
        this.stopTime.set(0L);
    }

    @Override
    public void completeSnapshot() {
        this.snapshotCompleted.set(true);
        this.snapshotAborted.set(false);
        this.snapshotRunning.set(false);
        this.stopTime.set(clock.currentTimeInMillis());
    }

    @Override
    public void abortSnapshot() {
        this.snapshotCompleted.set(false);
        this.snapshotAborted.set(true);
        this.snapshotRunning.set(false);
        this.stopTime.set(clock.currentTimeInMillis());
    }

    @Override
    public void setRowsScanned(TableId tableId, long numRows) {
        rowsScanned.put(tableId.toString(), numRows);
    }

    @Override
    public ConcurrentMap<String, Long> getRowsScanned() {
        return rowsScanned;
    }

    @Override
    public void reset() {
    }
}