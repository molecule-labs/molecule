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

package molecule.examples.core.rxlike

import molecule._
import molecule.stream._

/**
 * See:
 * http://techblog.netflix.com/2013/02/rxjava-netflix-api.html
 */
object NetflixBlog extends App {

  // Ugly
  def customObservableNonBlocking: IChan[String] = {
    @volatile var counter: Int = 0
    Observable.create[String](observer => {
      observer.onNext("anotherValue_" + counter)
      counter += 1
      if (counter == 75)
        observer.onComplete()
    })
  }

  // Good
  def customStream: IChan[String] = IChan.from(0).take(75).map("anotherValue_" + _)

  import process.Process

  def simpleComposition(in: IChan[String]): Process[Unit] = {
    // fetch an asynchronous Observable<String>
    // that emits 75 Strings of 'anotherValue_#'
    in
      // skip the first 10
      .drop(10)
      // take the next 5
      .take(5)
      // transform each String with the provided function
      .map(_ + "_transformed")
      // subscribe to the sequence and print each transformed String
      .foreach(it => println("onNext => " + it))
  }

  val p = platform.Platform("client")
  p.launch(simpleComposition(customObservableNonBlocking)).get_!
  p.launch(simpleComposition(customStream)).get_!

  type T = Int
  var availableInMemory = false
  var valueFromMemory: T = 0

  import platform.executors.{ SingleThreadedExecutor }
  import platform.{ ThreadFactory }
  import channel.RIChan

  private[this] lazy val executor = SingleThreadedExecutor("netflix-blocking-service-worker")

  def getValueFromRemoteService(id: Int): T = 0

  /**
   * Non-blocking method that immediately returns the value
   * if available or uses a thread to fetch the value and
   * callback via `onNext()` when done.
   */
  def getData(id: Int): RIChan[T] =
    if (availableInMemory)
      // if data available return immediately with data
      RIChan.success(valueFromMemory)
    else
      // else spawn thread or async IO to fetch data
      RIChan.async(executor)(getValueFromRemoteService(id)).dispatchTo(p)

  /////////////////////////////////////////////////

  // Netflix API Mockups
  def getMetadataFromRemoteService(id: Int): Map[String, String] =
    Map("title" -> ("video" + id), "duration" -> utils.random().nextInt(4 * 60 * 60).toString)

  def getBookmarkFromRemoteService(videoId: Int, userId: Int): Int =
    utils.random().nextInt(60 * 60)

  case class Rating(actual: Int, average: Int, predicted: Int)
  def getRatingFromRemoteService(videoId: Int, userId: Int): Rating =
    Rating(utils.random().nextInt(11), utils.random().nextInt(11), utils.random().nextInt(11))

  case class Video(id: Int) {

    def getMetadata(): RIChan[Map[String, String]] =
      (RIChan.lazyAsync(executor)(getMetadataFromRemoteService(id))).cache().dispatchTo(p)

    def getBookmark(userId: Int): RIChan[Int] =
      RIChan.lazyAsync(executor)(getBookmarkFromRemoteService(id, userId)).dispatchTo(p)

    def getRating(userId: Int): RIChan[Rating] =
      RIChan.lazyAsync(executor)(getRatingFromRemoteService(id, userId)).dispatchTo(p)
  }

  def getVideoFromRemoteService(id: Int): Video = Video(id)

  def getVideo(id: Int): RIChan[Video] =
    RIChan.async(executor)(getVideoFromRemoteService(id)).dispatchTo(p)

  class VideoList(videoIds: List[Int]) {
    def getVideos: IChan[Video] =
      IChan.source(videoIds).mapMany(getVideo)
  }

  def getListOfLists(userId: Int): RIChan[VideoList] =
    RIChan.async(executor)(new VideoList((1 to 20).toList)).dispatchTo(p)

  // Groovy like property list
  class PList(private val ps: List[(String, Any)]) {
    def ::(p: (String, Any)): PList = new PList(p :: ps)
    def ++(xs: PList): PList = new PList(ps ++ xs.ps)
    override def toString =
      if (ps.isEmpty) "[]"
      else {
        val sb = new StringBuffer
        val (n, v) = ps.head
        sb.append("[").append(n + ":" + v)
        ps.tail.foldLeft(sb) { case (sb, (n, v)) => sb.append(", " + n + ":" + v) }
        sb.append("]")
        sb.toString
      }
  }
  object PList {
    def apply(ps: (String, Any)*): PList = new PList(ps.toList)
  }

  // End of mockup

  def getVideoGridForDisplay(userId: Int): IChan[PList] =
    getListOfLists(userId).mapMany(list => {
      // for each VideoList we want to fetch the videos
      list.getVideos
        .take(10) // we only want the first 10 of each list
        .mapMany(video => {
          // for each video we want to fetch metadata
          val m = video.getMetadata().map(md =>
            // transform to the data and format we want
            PList("title" -> md("title"), "length" -> md("duration")))

          val b = video.getBookmark(userId).map(position =>
            PList("bookmark" -> position))

          val r = video.getRating(userId).map(rating =>
            PList("Rating" ->
              PList(
                "actual" -> rating.actual,
                "average" -> rating.average,
                "predicted" -> rating.predicted)))

          // compose these together
          m.zip(b).zip(r).map {
            case ((metadata, bookmark), rating) =>
              // now transform to complete dictionary of data
              // we want for each Video
              ("id" -> video.id) :: metadata ++ bookmark ++ rating
          }
        })
    })

  p.launch(getVideoGridForDisplay(1).foreach(println)).get_!

  p.shutdown()

  // emits results such as
  //[id:1002, title:video-1002-title, length:5428, bookmark:0,
  //rating:[actual:2, average:4, predicted:3]]
  //[id:1003, title:video-1003-title, length:5428, bookmark:0,
  //rating:[actual:4, average:4, predicted:4]]
  //[id:1004, title:video-1004-title, length:5428, bookmark:0,
  //rating:[actual:4, average:1, predicted:1]]
}