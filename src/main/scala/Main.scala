import zio.*
import zio.Console.*
import zio.schema.*

import java.time.{LocalDateTime, ZoneId}
// import zio.schema.Schema.*
import zio.schema.codec.*
import scala.collection.JavaConverters.*
import java.util.UUID
import zio.json.*
import zio.schema.{DeriveSchema, Schema}
import zio.stream.{Stream, ZStream}

object NamespacesProcessor:
  private type Namespaces = Map[ID, Model.Namespace]
  private val empty: Namespaces  = Map.empty[ID, Model.Namespace]
  private def now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
  private type CommandHandler[Cmd <: Command, E <: Event] = (Namespaces, Cmd) => Task[(Namespaces, E)]
  private type EventHandler[E <: Event]                   = (Namespaces, E) => (Namespaces, E)

  private val findNamespaceById: (Namespaces, ID) => Task[Model.Namespace] = (namespaces, id) =>
    ZIO
      .fromOption(namespaces.find((namespaceID, namespace) => namespaceID == id && namespace.deletedAt.isEmpty))
      .mapError(_ => new RuntimeException(s"Namespace $id was not found."))
      .map(_._2)

  private val createNamespace: CommandHandler[CreateNamespace, NamespaceCreated] = {
    case (namespaces, CreateNamespace(id, name)) =>
      findNamespaceById(namespaces, id).foldZIO(
        _ => ZIO.succeed(namespaces -> NamespaceCreated(name, id)),
        namespace => ZIO.fail(new RuntimeException(s"Namespace $name already exists."))
      )
  }

  private val namespaceCreated: EventHandler[NamespaceCreated] = { case (namespaces, event) =>
    namespaces.updated(
      event.id,
      Model.Namespace.empty.copy(event.id, event.name, updatedAt = now, createdAt = now, deletedAt = None)
    ) -> event
  }

  private val renameNamespace: CommandHandler[RenameNamespace, NamespaceRenamed] = {
    case (namespaces, RenameNamespace(id, name)) =>
      findNamespaceById(namespaces, id).map(namespace => namespaces -> NamespaceRenamed(name, id))
  }

  private val namespaceRenamed: EventHandler[NamespaceRenamed] = {
    case (namespaces, event @ NamespaceRenamed(name, id, _)) =>
      namespaces.updatedWith(id) {
        case Some(namespace) => Some(namespace.copy(name = name))
        case _               => None
      } -> event
  }

  private val deleteNamespace: CommandHandler[DeleteNamespace, NamespaceDeleted] = {
    case (namespaces, DeleteNamespace(id)) =>
      findNamespaceById(namespaces, id).map(namespace => namespaces -> NamespaceDeleted(id))
  }

  private val namespaceDeleted: EventHandler[NamespaceDeleted] = { case (namespaces, event @ NamespaceDeleted(id, _)) =>
    namespaces.updatedWith(id) {
      case Some(namespace) => Some(namespace.copy(deletedAt = Some(now)))
      case _               => None
    } -> event
  }

  private val createEvent: CommandHandler[CreateEvent, EventCreated] = {
    case (namespaces, CreateEvent(namespaceID, eventID, name, dateTime, tags)) =>
      findNamespaceById(namespaces, namespaceID).map(namespace =>
        namespaces -> EventCreated(eventID, namespaceID, name, dateTime, tags)
      )
  }

  private val eventCreated: EventHandler[EventCreated] = {
    case (namespaces, event @ EventCreated(eventID, namespaceID, name, dateTime, tags, _)) =>
      namespaces.updatedWith(namespaceID) {
        case Some(namespace) =>
          Some(
            namespace.copy(events =
              (namespace.events ++ Seq(
                Model.Event.empty.copy(
                  id = eventID,
                  name = name,
                  tags = tags,
                  dateTime = dateTime
                )
              )).distinctBy(_.id)
            )
          )
        case _               => None
      } -> event
  }

  private val renameEvent: CommandHandler[RenameEvent, EventRenamed] = {
    case (namespaces: Namespaces, RenameEvent(namespaceID, id, name)) =>
      findNamespaceById(namespaces, namespaceID)
        .map(namespace => namespaces -> EventRenamed(id, namespaceID, name))
  }

  private val eventRenamed: EventHandler[EventRenamed] = {
    case (namespaces, event @ EventRenamed(id, namespaceID, name, _)) =>
      namespaces.updatedWith(namespaceID) {
        case Some(namespace) =>
          Some(
            namespace.copy(events = namespace.events.map {
              case event if event.id == id => event.copy(name = name)
              case event                   => event
            })
          )
        case _               => None
      } -> event
  }

  private val deleteEvent: CommandHandler[DeleteEvent, EventDeleted] = {
    case (namespaces: Namespaces, DeleteEvent(namespaceID, id)) =>
      findNamespaceById(namespaces, namespaceID)
        .map(namespace => namespaces -> EventDeleted(id, namespaceID))
  }

  private val eventDeleted: EventHandler[EventDeleted] = {
    case (namespaces, event @ EventDeleted(id, namespaceID, _)) =>
      namespaces.updatedWith(namespaceID) {
        case Some(namespace) =>
          Some(namespace.copy(events = namespace.events.filterNot {
            case event if event.id == id => true
            case event                   => false
          }))
        case _               => None
      } -> event
  }

  private val processCommand: ((Namespaces, Command)) => ZIO[Any, Throwable, (Namespaces, Event)] = {
    case (state, command: CreateNamespace) => createNamespace(state, command)
    case (state, command: RenameNamespace) => renameNamespace(state, command)
    case (state, command: DeleteNamespace) => deleteNamespace(state, command)
    case (state, command: CreateEvent)     => createEvent(state, command)
    case (state, command: RenameEvent)     => renameEvent(state, command)
    case (state, command: DeleteEvent)     => deleteEvent(state, command)
  }

  private val processEvent: ((Namespaces, Event)) => (Namespaces, Event) = {
    case (state, event: NamespaceCreated) => namespaceCreated(state, event)
    case (state, event: NamespaceRenamed) => namespaceRenamed(state, event)
    case (state, event: NamespaceDeleted) => namespaceDeleted(state, event)
    case (state, event: EventCreated)     => eventCreated(state, event)
    case (state, event: EventDeleted)     => eventDeleted(state, event)
    case (state, event: EventRenamed)     => eventRenamed(state, event)
  }

  private def persistEvent[E <: Event](event: E): Task[Unit] =
    Console.printLine(s"PersistEvent: ${event}")

  def fromCommands(commands: ZStream[Any, Throwable, Command]): ZStream[Any, Throwable, Event] =
    commands.mapAccumZIO(empty) { case (state, cmd) => processCommand(state, cmd).map(processEvent) }

  def fromEvents(events: ZStream[Any, Throwable, Event]): ZStream[Any, Throwable, Event] =
    events.mapAccumZIO(empty) { case (state, event) => ZIO.succeed(processEvent(state, event)) }

  def namespacesFromCommands(commands: ZStream[Any, Throwable, Command]): Task[Option[Namespaces]] =
    commands
      .mapAccumZIO(empty) { case (state, cmd) =>
        processCommand(state, cmd)
          .map(processEvent)
          .tap((_, event) => persistEvent(event))
          .map { case (newState, event) => (newState, newState) }
      }
      .runLast

  def namespacesFromEvents(commands: ZStream[Any, Throwable, Event]): Task[Option[Namespaces]] =
    commands
      .mapAccumZIO(empty) { case (state, event) =>
        ZIO.succeed(processEvent(state, event)).map { case (newState, event) => (newState, newState) }
      }
      .runLast

object Main extends ZIOAppDefault:
  import Event.schema.*

  def run = program

  private def program = for
    _ <- Console.printLine("Hello")
    ids @ (n1, n2, n3, n4)  = (ID.mkID, ID.mkID, ID.mkID, ID.mkID)
    eventIDs @ (e1, e2, e3) = (ID.mkID, ID.mkID, ID.mkID)
    commands                = ZStream.fromIterable(
      Seq(
        CreateNamespace(n1, "Namespace A"),
        CreateNamespace(n2, "Namespace B"),
        CreateNamespace(n3, "Namespace C"),
        RenameNamespace(n1, "Oto Space"),
        RenameNamespace(n2, "Power"),
        DeleteNamespace(n3),
        // DeleteNamespace(n3),
        CreateEvent(n1, e1, "Send out invoices I"),
        CreateEvent(n1, e2, "Send out invoices II."),
        CreateEvent(n1, e3, "Send out invoices III."),
        RenameEvent(n1, e1, "TODO 1"),
        RenameEvent(n2, e2, "TODO 2"),
        DeleteEvent(n1, e3),
        CreateNamespace(n4, "Testing is fun")
      )
    )
    events <- NamespacesProcessor.fromCommands(commands).runCollect
    rawEvents = ZStream.fromIterable(events)

    // _      <- Console.printLine(events.mkString("\n"))
    // pok <- NamespacesProcessor.fromEvents(eventsStream).runDrain

    nss <- NamespacesProcessor.namespacesFromCommands(commands).flatMap(ZIO.fromOption(_))
    _   <- ZStream
      .fromIterable(nss.values)
      .tap(ns =>
        val json = Model.Namespace.namespaceJsonEncoder.encodeJson(ns, Some(2))
        Console.printLine(json)
      )
      .runDrain

    ens <- NamespacesProcessor.namespacesFromEvents(rawEvents).flatMap(ZIO.fromOption(_))
    _   <- ZStream
      .fromIterable(ens.values)
      .tap(ns =>
        val json = Model.Namespace.namespaceJsonEncoder.encodeJson(ns, Some(2))
        Console.printLine(json)
      )
      .runDrain
  yield ()
