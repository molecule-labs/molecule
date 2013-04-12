/*
 * Copyright (C) 2013 Alcatel-Lucent.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package molecule
package channel

/**
 * Factory for multiple-producers, single-consumer cooperative channels
 *
 * This channel is cooperative and bounded. After it has written a segment, a (producer) user-level
 * thread becomes suspended when it writes its next segment until the previous segment has been
 * entirely delivered to the (consumer) user-level reading on the input interface of this channel.
 *
 * This channel can only be poisoned from the input side. In other words, any attempt
 * to close an output channel interface associated to a many-to-one channel will be ignored.
 */
object ManyToOne {

  /**
   * Create a many to one channel.
   *
   * The channel may aggregate segments emitted concurrently by multiple producers into
   * a single batch. The behavior depends on the `maxSegmentSize` passed as parameter to
   * this factory.
   *
   * If `maxSegmentSize` is greater than 0, and the reader is slow, then the channel attempts to
   * aggregate multiple segments into a single segment whose size cannot exceed `maxSegmentSize`.
   *
   * If `maxSegmentSize` is equal to 0, then the channel doesn't perform any aggregation and delivers
   * segments one-by-one in the order in which they are produced.
   *
   * The maximum number of messages buffered in the channel is equal
   * to N * 2 * MaxInputSegmentSize, where:
   * - N is the number of concurrent producers
   * - MaxInputSegmentSize is the maximum size of the segments emitted by producers
   * (in principle, bounded by Platform.segmentSizeThreshold).
   *
   * @param maxSegmentSize If it is greater than 0, the parameter represents the maximum size
   *   of the aggregated segments that can be read from the input side of the channel. If it is equal to 0,
   *   the segments written to the channel will not be aggregated and read one by one.
   *
   * @return the input interfaces of the channel and a factory method for creating an individual
   *         output channel interface for each producer.
   */
  def mk[A: Message](maxSegmentSize: Int = 0): (IChan[A], OChanFactory[A]) = {
    assert(maxSegmentSize >= 0, "Maximum segment size must be greater than or equal to zero (got:" + maxSegmentSize + ")")
    impl.ManyToOneBChan(maxSegmentSize)
  }

}