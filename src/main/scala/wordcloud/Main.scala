package wordcloud

import akka.actor._
import scala.concurrent.duration._
import spray.routing.HttpServiceActor
import akka.io.IO
import spray.can.Http
import spray.http.HttpHeaders
import spray.http.CacheDirectives
import spray.http.MediaTypes
import spray.http.MediaTypes._
import spray.http.MediaType
import spray.http.ChunkedResponseStart
import spray.routing.RequestContext
import spray.http.HttpResponse
import spray.http.HttpEntity
import spray.http.MessageChunk

object Wordcloud {
  case class NewStatus(text: String)
  case object SendStatus
  case class Register(ref: ActorRef, query: String)

  val stopwords = Set("a", "about", "above", "across", "after", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "among", "an", "and", "another", "any", "anybody", "anyone", "anything", "anywhere", "are", "area", "areas", "around", "as", "ask", "asked", "asking", "asks", "at", "away", "b", "back", "backed", "backing", "backs", "be", "became", "because", "become", "becomes", "been", "before", "began", "behind", "being", "beings", "best", "better", "between", "big", "both", "but", "by", "c", "came", "can", "cannot", "case", "cases", "certain", "certainly", "clear", "clearly", "come", "could", "d", "did", "differ", "different", "differently", "do", "does", "done", "down", "down", "downed", "downing", "downs", "during", "e", "each", "early", "either", "end", "ended", "ending", "ends", "enough", "even", "evenly", "ever", "every", "everybody", "everyone", "everything", "everywhere", "f", "face", "faces", "fact", "facts", "far", "felt", "few", "find", "finds", "first", "for", "four", "from", "full", "fully", "further", "furthered", "furthering", "furthers", "g", "gave", "general", "generally", "get", "gets", "give", "given", "gives", "go", "going", "good", "goods", "got", "great", "greater", "greatest", "group", "grouped", "grouping", "groups", "h", "had", "has", "have", "having", "he", "her", "here", "herself", "high", "high", "high", "higher", "highest", "him", "himself", "his", "how", "however", "i", "if", "important", "in", "interest", "interested", "interesting", "interests", "into", "is", "it", "its", "itself", "j", "just", "k", "keep", "keeps", "kind", "knew", "know", "known", "knows", "l", "large", "largely", "last", "later", "latest", "least", "less", "let", "lets", "like", "likely", "long", "longer", "longest", "m", "made", "make", "making", "man", "many", "may", "me", "member", "members", "men", "might", "more", "most", "mostly", "mr", "mrs", "much", "must", "my", "myself", "n", "necessary", "need", "needed", "needing", "needs", "never", "new", "new", "newer", "newest", "next", "no", "nobody", "non", "noone", "not", "nothing", "now", "nowhere", "number", "numbers", "o", "of", "off", "often", "old", "older", "oldest", "on", "once", "one", "only", "open", "opened", "opening", "opens", "or", "order", "ordered", "ordering", "orders", "other", "others", "our", "out", "over", "p", "part", "parted", "parting", "parts", "per", "perhaps", "place", "places", "point", "pointed", "pointing", "points", "possible", "present", "presented", "presenting", "presents", "problem", "problems", "put", "puts", "q", "quite", "r", "rather", "really", "right", "right", "room", "rooms", "s", "said", "same", "saw", "say", "says", "second", "seconds", "see", "seem", "seemed", "seeming", "seems", "sees", "several", "shall", "she", "should", "show", "showed", "showing", "shows", "side", "sides", "since", "small", "smaller", "smallest", "so", "some", "somebody", "someone", "something", "somewhere", "state", "states", "still", "still", "such", "sure", "t", "take", "taken", "than", "that", "the", "their", "them", "then", "there", "therefore", "these", "they", "thing", "things", "think", "thinks", "this", "those", "though", "thought", "thoughts", "three", "through", "thus", "to", "today", "together", "too", "took", "toward", "turn", "turned", "turning", "turns", "two", "u", "under", "until", "up", "upon", "us", "use", "used", "uses", "v", "very", "w", "want", "wanted", "wanting", "wants", "was", "way", "ways", "we", "well", "wells", "went", "were", "what", "when", "where", "whether", "which", "while", "who", "whole", "whose", "why", "will", "with", "within", "without", "work", "worked", "working", "works", "would", "x", "y", "year", "years", "yet", "you", "young", "younger", "youngest", "your", "yours")
}

class Wordcloud extends Actor {
  import Wordcloud._

  implicit val ec = context.dispatcher

  var freqs = Map.empty[String, Long]
  var responders = Set.empty[ActorRef]
  var needsSending = false

  val wordRegex = """[\-,:\.#"'\s\@\!\¡\…\?\¿]""".r
  val urlRegex = """(https?://([-\w\.]+)+(:\d+)?(/([\w/_\.]*(\?\S+)?)?)?)""".r

  val twitterStream = new twitter4j.TwitterStreamFactory().getInstance()

  override def preStart() {
    super.preStart()
    context.system.scheduler.schedule(2.second, 2.seconds, self, SendStatus)
  }

  def updateFreqs(text: String): Unit = {
    freqs = wordRegex
      .split(urlRegex.replaceAllIn(text, ""))
      .map(_.trim.toLowerCase)
      .filter(w => !w.isEmpty() && !stopwords.contains(w))
      .foldLeft(freqs) { (f: Map[String, Long], w: String) =>
        f.updated(w, f.getOrElse(w, 0L) + 1)
      }
  }

  def startListener(query: String) = {
    import twitter4j._

    val listener = new StatusListener {
      override def onStatus(status: Status) {
        self ! NewStatus(status.getText())
      }
      override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
      override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}
      override def onException(ex: Exception) {
        ex.printStackTrace()
      }
      override def onScrubGeo(userId: Long, upToStatusId: Long) {}
      override def onStallWarning(warning: StallWarning) {}

    }

    val filter = new FilterQuery()
    filter.track(Array(query))

    twitterStream.addListener(listener)
    println("Starting stream")
    twitterStream.filter(filter)
  }

  def receive: Receive = {
    case NewStatus(t) =>
      updateFreqs(t)
      needsSending = true
    case SendStatus =>
      if (needsSending) {
        val sortedFreqs = freqs.toList.sortBy(_._2)

        val response = "{" + sortedFreqs.map {
          case (w, f) => s""""$w": $f"""
        }.mkString(",\n") + "},\n"

        responders.foreach(_ ! MessageChunk(response))

        needsSending = false
      }
    case Register(responder, query) =>
      println("Registering responder")
      responders += responder
      startListener(query)
    case msg =>
      sys.error(s"Invalid message $msg (sender is: $sender)")
  }
}

object WordcloudHttpService {
  val `text/event-stream` = MediaType.custom("text/event-stream")
  MediaTypes.register(`text/event-stream`)
}

class WordcloudHttpService(streamer: ActorRef) extends HttpServiceActor {
  import HttpHeaders.{ `Cache-Control`, `Connection` }
  import CacheDirectives.`no-cache`
  import WordcloudHttpService._
  import Wordcloud._

  implicit val ec = context.dispatcher

  def respondAsEventStream =
    respondWithHeader(`Cache-Control`(`no-cache`)) &
      respondWithHeader(`Connection`("Keep-Alive")) &
      respondWithMediaType(`text/event-stream`)

  def receive: Receive = runRoute {
    path("wordcloud" / Segment) { query =>
      get {
        respondAsEventStream { ctx =>
          println(s"Query: $query")
          val responseStart = HttpResponse(entity = HttpEntity(`text/html`, "[\n"))
          ctx.responder ! ChunkedResponseStart(responseStart)
          streamer ! Register(ctx.responder, query)
        }
      }
    }
  }
}

object Main {
  import Wordcloud._

  def main(args: Array[String]) {
    implicit val system = ActorSystem("wordcloud")
    val wordcloudActor = system.actorOf(Props[Wordcloud])
    val httpService = system.actorOf(Props(classOf[WordcloudHttpService], wordcloudActor))

    IO(Http) ! Http.Bind(httpService, interface = "localhost", port = 9909)
  }
}
