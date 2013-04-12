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
 * Factory for cooperative buffered channels.
 *
 * A user-level thread writing to this channel will become suspended if the size of the segment
 * it writes to the channel plus the sum of the sizes of the segments already buffered by the channel
 * exceed the size specified at creation time. In case the reader is slower than the producer,
 * multiple segments buffered by the channel will be aggregated together into a single segment,
 * which will be delivered the next time a user-level thread reads on the channel.
 *
 */
object Buffer {

  /**
   * Create a buffered channel backed up by a fixed size buffer.
   *
   * The buffer is read into a single segment whose maximum size is
   * fixed by the maxSegmentSize parameter.
   *
   * The maximum number of messages buffered by the channel is equal to
   * ((size - 1) + MaxInputSegmentSize), where
   * - size is the maximum size of the segment that can be read from the channel.
   * - MaxInputSegmentSize is the maximum size of the segments emitted by producers
   * (in principle, bounded by Platform.segmentSizeThreshold).
   *
   * @param size the maximum segment size that can be read from input side of the channel.
   * @return the input and output interfaces of the buffered channel.
   */
  def mk[A: Message](size: Int): (IChan[A], OChan[A]) = {
    assert(size > 0, "Buffer size must be greater than zero (got:" + size + ")")
    impl.OneToOneBChan(size)
  }
}