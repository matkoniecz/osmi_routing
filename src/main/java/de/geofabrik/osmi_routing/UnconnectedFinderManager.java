/*
 *  © 2019 Geofabrik GmbH
 *
 *  Multithreading implementation is inspired by GraphHopper and Osmosis.
 *  Copyright 2012 - 2017 GraphHopper GmbH, licensed unter Apache License
 *  version 2.
 *
 *  This file is part of osmi_routing.
 *
 *  osmi_routing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License.
 *
 *  osmi_routing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with osmi_simple_views. If not, see <http://www.gnu.org/licenses/>.
 */

package de.geofabrik.osmi_routing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfBlobDecoderListener;
import com.graphhopper.reader.osm.pbf.PbfBlobResult;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.AngleCalc;

import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder;
import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder.RoadClass;
import de.geofabrik.osmi_routing.reader.BarriersHook;

public class UnconnectedFinderManager {

        static final Logger logger = LogManager.getLogger(UnconnectedFinderManager.class.getName());

        private GraphHopperSimple hopper;
        private GraphHopperStorage storage;
        OsmIdAndNoExitStore nodeInfoStore;
        BarriersHook barriersHook;
        boolean doRouting;
        AllRoadsFlagEncoder encoder;
        GeoJSONWriter writer;
        private double maxDistance;
        AngleCalc angleCalc;
        private final Lock lock;
        private final Condition dataWaitCondition;
        private final Queue<MissingConnectionResult> results;
        ExecutorService executorService;
        private int threadCount;
        HashMap<RoadClass, int[]> priorities;
        private int increment = 100000;

        public UnconnectedFinderManager(GraphHopperSimple hopper, AllRoadsFlagEncoder encoder, Path outputPath, double maxDistance, int workers) throws IOException {
            this.hopper = hopper;
            this.encoder = encoder;
            this.writer = new GeoJSONWriter(outputPath);
            this.maxDistance = maxDistance;
            this.angleCalc = new AngleCalc();

            // Create the thread synchronisation primitives.
            lock = new ReentrantLock();
            dataWaitCondition = lock.newCondition();

            // Create the queue of blobs being decoded.
            results = new LinkedList<MissingConnectionResult>();
            
            this.threadCount = workers;
            executorService = Executors.newFixedThreadPool(threadCount);
            initPriorities();
        }

        private void initPriorities() {
            priorities = new HashMap<AllRoadsFlagEncoder.RoadClass, int[]>();
            priorities.put(RoadClass.MOTORWAY,      new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.MOTORWAY_LINK, new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.TRUNK,         new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.TRUNK_LINK,    new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.PRIMARY,       new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.PRIMARY_LINK,  new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.SECONDARY,     new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.SECONDARY_LINK, new int[]{1, 1, 1, 2});
            priorities.put(RoadClass.TERTIARY,      new int[]{2, 2, 3, 3});
            priorities.put(RoadClass.TERTIARY_LINK, new int[]{2, 2, 3, 3});
            priorities.put(RoadClass.UNCLASSIFIED,  new int[]{2, 2, 3, 3});
            priorities.put(RoadClass.RESIDENTIAL,   new int[]{2, 2, 3, 3});
            priorities.put(RoadClass.LIVING_STREET, new int[]{2, 3, 3, 4});
            priorities.put(RoadClass.TRACK,         new int[]{2, 2, 3, 4});
            priorities.put(RoadClass.PEDESTRIAN,    new int[]{3, 4, 4, 5});
            priorities.put(RoadClass.FOOTWAY,       new int[]{4, 5, 5, 5});
            priorities.put(RoadClass.CYCLEWAY,      new int[]{4, 5, 5, 5});
            priorities.put(RoadClass.PATH,          new int[]{4, 5, 5, 5});
            priorities.put(RoadClass.STEPS,         new int[]{5, 5, 5, 5});
            priorities.put(RoadClass.SERVICE,       new int[]{3, 4, 4, 4});
            priorities.put(RoadClass.SERVICE_ALLEY, new int[]{3, 4, 4, 5});
            priorities.put(RoadClass.SERVICE_DRIVEWAY, new int[]{4, 4, 5, 5});
            priorities.put(RoadClass.SERVICE_PARKING_AISLE, new int[]{4, 4, 5, 5});
            priorities.put(RoadClass.ROAD,          new int[]{3, 3, 4, 4});
            priorities.put(RoadClass.RACEWAY,       new int[]{2, 2, 2, 3});
            priorities.put(RoadClass.FERRY,         new int[]{2, 3, 3, 3});
            priorities.put(RoadClass.PLATFORM,      new int[]{4, 5, 5, 6});
        }

        private void waitForUpdate() {
            try {
                dataWaitCondition.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Thread was interrupted.", e);
            }
        }

        private void signalUpdate() {
            dataWaitCondition.signal();
        }

        private void sendResultsToSink(int targetQueueSize) {
            while (results.size() > targetQueueSize) {
                // Get the next result from the queue and wait for it to complete.
                MissingConnectionResult r = results.remove();
                while (!r.isComplete()) {
                    // The thread hasn't finished processing yet so wait for an
                    // update from another thread before checking again.
                    waitForUpdate();
                }

                try {
                    if (!r.isSuccess()) {
                        throw new RuntimeException("A worker thread failed, aborting.", r.getException());
                    }
                    lock.unlock();
                    writer.write(r.getEntities());
                    writer.flush();
                } catch (Exception e) {
                    logger.fatal(e);
                    System.exit(1);
                } finally {
                    lock.lock();
                }
            }
        }
        
        public void process() {
            int nodes = storage.getNodes();
            int lastLogId = 0;
            final int logInterval = nodes / 8;
            logger.info("Detection of unconnected roads: 0 of {}", nodes);
            increment = 1000;
            for (int startId = 0; startId < nodes; startId += increment) {
                if (lastLogId + logInterval < startId) {
                    logger.info("Detection of unconnected roads: {} of {}", startId, nodes);
                    lastLogId = startId;
                }
                int count = increment;
                if (startId + count > nodes) {
                    count = nodes - startId;
                }
                final MissingConnectionResult processingResult = new MissingConnectionResult();
                results.add(processingResult);
                OutputListener listener = new OutputListener() {
                    @Override
                    public void error(Exception ex) {
                        lock.lock();
                        try {
                            logger.catching(ex);
                            // If something failed, we go on the safe side and abort.
                            System.exit(1);
                        } finally {
                            lock.unlock();
                        }
                    }
    
                    @Override
                    public void complete(List<MissingConnection> decodedEntities) {
                        lock.lock();
                        try {
                            processingResult.storeSuccessResult(decodedEntities);
                            signalUpdate();
    
                        } finally {
                            lock.unlock();
                        }
                    }
                };
                
                UnconnectedFinder f = new UnconnectedFinder(hopper, encoder, maxDistance, storage,
                        new ThreadSafeOsmIdNoExitStoreAccessor(nodeInfoStore), barriersHook, listener, startId,
                        count, priorities, doRouting);
                executorService.execute(f);
                sendResultsToSink(threadCount - 1);
            }
            sendResultsToSink(0);
            try {
                writer.close();
            } catch (IOException e) {
                logger.fatal(e);
                System.exit(1);
            }
            logger.info("finished writing");
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
        }

        public void run() {
            lock.lock();
            try {
                process();
            } finally {
                lock.unlock();
            }
            
        }

        public void init(GraphHopperStorage graphHopperStorage, OsmIdAndNoExitStore infoStore, BarriersHook barriersHook,
                boolean doRouting) {
            this.storage = graphHopperStorage;
            this.nodeInfoStore = infoStore;
            this.barriersHook = barriersHook;
            this.doRouting = doRouting;
        }

}
