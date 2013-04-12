/*
 * Written by Hanson Char and released to the public domain,
 * as explained at http://creativecommons.org/licenses/publicdomain
 */
package molecule.utils;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * An unbounded concurrent blocking queue implemented upon 
 * {@link java.util.concurrent.ConcurrentLinkedQueue ConcurrentLinkedQueue}.
 * <p>
 * Note there is currently no such class in Java 6.
 * <p>
 * In contrast to {@link java.util.concurrent.LinkedBlockingQueue LinkedBlockingQueue}
 * which is always bounded, a ConcurrentLinkedBlockingQueue is unbounded.
 * 
 * Adapted by Sebastien Bocq for a single consumer.
 *  
 * @author Hanson Char
 * @param <E> the type of elements held in this collection
 */
public class ConcurrentLinkedBlockingQueueSC<E> extends AbstractQueue<E>
        implements java.io.Serializable, BlockingQueue<E>
{
    private static final long serialVersionUID = -191767472599610115L;

    static class ThreadMarker {
        final Thread thread;
        // assumed parked until found otherwise.
        volatile boolean parked = true;
        
        ThreadMarker(Thread thread)
        {
            this.thread = thread;
        }
    }
    
    private final ThreadMarker marker;
    
    private final ConcurrentLinkedQueue<E> q;

    public ConcurrentLinkedBlockingQueueSC(Thread thread) {
        q = new ConcurrentLinkedQueue<E>();
        this.marker = new ThreadMarker(thread);
    }

    @Override
    public Iterator<E> iterator() {
        return q.iterator();
    }

    @Override
    public int size() {
        return q.size();
    }

    public boolean offer(E e) {
        q.offer(e);
        
        if (marker.parked) {
          LockSupport.unpark(marker.thread);
        }
        return true;
    }

    public E peek() {
        return q.peek();
    }

    public E poll() {
        return q.poll();
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until
     * an element becomes available.
     * 
     * @return the head of this queue
     * @throws InterruptedException if interrupted while waiting
     */
    public E take() throws InterruptedException 
    {
      for (;;) {
        E e = q.poll();

        if (e != null)
            return e;
        
        marker.parked = true;
        
        if (Thread.interrupted())
        {
            throw new InterruptedException();
        }

        // check again in case there is data race
        e = q.poll();
        
        if (e != null) { // data race
          marker.parked = false;
          return e;
        }
        
        LockSupport.park();
        marker.parked = false;
      }            
    }
    
    /**
     * Retrieves and removes the head of this queue, waiting up to the specified
     * wait time if necessary for an element to become available.
     * 
     * @param timeout
     *            how long to wait before giving up, in units of <tt>unit</tt>.
     *            A negative timeout is treated the same as to wait forever.
     * @param unit
     *            a <tt>TimeUnit</tt> determining how to interpret the
     *            <tt>timeout</tt> parameter
     * @return the head of this queue, or <tt>null</tt> if the specified
     *         waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    public E poll(final long timeout, final TimeUnit unit) throws InterruptedException 
    {
        if (timeout < 0)
            return take();  // treat -ve timeout same as to wait forever
        final long t1 = System.nanoTime() + unit.toNanos(timeout);
        
        for (;;) {
            E e = q.poll();

            if (e != null)
                return e;
            final long duration = t1 - System.nanoTime();
            
            if (duration <= 0)
                return null;    // time out
            marker.parked = true;
            
            // check again in case there is data race
            e = q.poll();

            if (e != null)
            {   // data race indeed
                marker.parked = false;
                return e;
            }
            LockSupport.parkNanos(duration);
            marker.parked = false;
            
        }
    }
    
    public void put(E e) throws InterruptedException {
        q.add(e);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return this.offer(e);
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    public int drainTo(Collection<? super E> c) {
        int i = 0;
        E e;

        for (; (e=q.poll()) != null; i++)
            c.add(e);
        return i; 
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        int i = 0;
        E e;

        for (; i < maxElements && (e=q.poll()) != null; i++)
            c.add(e);
        return i; 
    }
}