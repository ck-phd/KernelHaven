package net.ssehub.kernel_haven.util;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * A utility class for periodically logging the progress of a long-running task.
 * 
 * @author Adam
 */
public class ProgressLogger implements Closeable {

    private static final ProgressLoggerThread LOG_THREAD = new ProgressLoggerThread();
    
    /**
     * The interval to log in, in milliseconds.
     */
    private static int interval = 30000;
    
    private @NonNull String task;
    
    private int numItems;
    
    private @NonNull AtomicInteger doneItems;
    
    private @NonNull AtomicBoolean finished;
    
    /**
     * Creates a  new {@link ProgressLogger} for the given task, without an estimated number of items to process.
     * 
     * @param task The name of the task. This will appear in the log.
     */
    public ProgressLogger(@NonNull String task) {
        this(task, -1);
    }
    
    /**
     * Creates a  new {@link ProgressLogger} for the given task, with an estimated number of items to process.
     * 
     * @param task The name of the task. This will appear in the log.
     * @param numItems The number of items to process. The percentage of progress is calculated from this. -1 if if no
     *      number of items can be estimated.
     */
    public ProgressLogger(@NonNull String task, int numItems) {
        this.task = task;
        this.numItems = numItems;
        this.doneItems = new AtomicInteger(0);
        this.finished = new AtomicBoolean(false);
        
        LOG_THREAD.add(this);
    }
    
    /**
     * Signals that one item is done.
     */
    public void oneDone() {
        doneItems.incrementAndGet();
    }
    
    /**
     * Signals that a number of items are done.
     * 
     * @param numDone The number of items that are done. This is added to the previous amount of finished items.
     */
    public void done(int numDone) {
        doneItems.addAndGet(numDone);
    }
    
    /**
     * Signals that the task is done. This should be called, even if the number of items is reached via done() calls.
     */
    @Override
    public void close() {
        this.finished.set(true);
    }
    
    /**
     * The thread that periodically logs the status of the {@link ProgressLogger}s.
     */
    private static class ProgressLoggerThread extends Thread {
        
        private List<@NonNull ProgressLogger> list;
        
        /**
         * Creates and starts this logger. Used only for the singleton instance.
         */
        ProgressLoggerThread() {
            super("ProgressLogger");
            
            this.list = new LinkedList<>();
            
            setDaemon(true); // this thread will run forever
            start();
        }

        /**
         * Adds a new {@link ProgressLogger} to observe. This will be observed until
         * 
         * @param progressLogger The {@link ProgressLogger} to add.
         */
        void add(@NonNull ProgressLogger progressLogger) {
            synchronized (list) {
                list.add(progressLogger);
            }
        }
        
        @Override
        public void run() {
            while (true) {
                List<String> lines;
                
                synchronized (list) {
                    lines = new ArrayList<>(list.size());
                    
                    for (int i = 0; i < list.size(); i++) {
                        ProgressLogger logger = notNull(list.get(i));
                        
                        int max = logger.numItems;
                        int current = logger.doneItems.get();
                        boolean done = logger.finished.get();
                        
                        if (max >= 0) {
                            lines.add(String.format("%s finished %d of %d (%d%%) items"
                                    + (done ? " and is done" : ""),
                                    logger.task, current, max, (int) (current * 100.0 / max)));
                        } else {
                            lines.add(String.format("%s finished %d items" + (done ? " and is done" : ""),
                                    logger.task, current));
                        }

                        // only remove after we logged the final message
                        if (done) {
                            list.remove(i);
                            i--; // decrement because list is moved up
                        }
                    }
                }
                
                if (!lines.isEmpty()) {
                    Logger.get().logInfo(lines.toArray(new String[0]));
                }
                
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        
    }
    
}