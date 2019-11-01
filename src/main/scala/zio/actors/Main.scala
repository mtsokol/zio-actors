package zio.actors

import zio.actors.Actor.Stateful
import zio.{App, IO, UIO}
import zio.console._

object Main extends App {

  sealed trait Message[+A]
  case class Str(value: String) extends Message[String]

  val handler = new Stateful[Int, Any, Message] {
    override def receive[A](state: Int, msg: Message[A], system: ActorSystem): IO[Any, (Int, A)] =
      msg match {
        case Str(value) =>
          IO((1, value + "1"))
      }
  }

  sealed trait PingPongProto[+A]
  case class Ping(sender: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]
  case object Pong extends PingPongProto[Unit]
  case class GameInit(sender: ActorRef[Exception, PingPongProto],
                      recipient: ActorRef[Exception, PingPongProto]) extends PingPongProto[Unit]

  val protoHandler = new Stateful[Unit, Exception, PingPongProto] {
    override def receive[A](state: Unit, msg: PingPongProto[A], system: ActorSystem): IO[Exception, (Unit, A)] =
      msg match {
        case Ping(sender) => (for {
          path <- sender.path
          _ <- putStrLn(s"Ping from: $path, sending pong")
          _ <- (sender ! Pong).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case Pong => (for {
          _ <- putStrLn("Received pong")
          _ <- IO.succeed(1)
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]

        case GameInit(from, to) => (for {
          _ <- putStrLn("The game starts...")
          _ <- (to ! Ping(from)).fork
        } yield ((), ())).asInstanceOf[IO[Exception, (Unit, A)]]
      }
  }

  def run(args: List[String]) =
    myAppLogic3.fold(_ => 1, _ => 0)

  val myAppLogic =
    for {
      actorSystem <- ActorSystem("myActorSystem", Some("127.0.0.1", 9097))
      r <- actorSystem.createActor("firstActor", Supervisor.none, 0, handler)
      t <- r.!(Str("aa"))
      _ <- putStrLn(t)
      _ <- UIO(1).forever
    } yield ()

  val myAppLogic2 =
    for {
      _ <- zio.console.putStrLn("XDD")
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9080))
      _ <- actorSystemRoot.createActor("actorOne", Supervisor.none, 0, handler)
      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9081))
      actorRef <- actorSystem.selectActor[Any, Message]("zio://testSystemOne@127.0.0.1:9080/actorOne")
      result <- actorRef ! Str("ZIO-Actor response... ")
      _ <- zio.console.putStrLn(result)
    } yield ()

  val myAppLogic3 =
    for {
      actorSystemRoot <- ActorSystem("testSystemOne", Some("127.0.0.1", 9165))
      one <- actorSystemRoot.createActor("actorOne", Supervisor.none, (), protoHandler)

      actorSystem <- ActorSystem("testSystemTwo", Some("127.0.0.1", 9166))
      two <- actorSystem.createActor("actorTwo", Supervisor.none, (), protoHandler)

      remotee <- actorSystemRoot.selectActor[Exception, PingPongProto]("zio://testSystemTwo@127.0.0.1:9166/actorTwo")

      _ <- one ! GameInit(one, remotee)
      _ <- IO.unit.forever
    } yield ()

}
