import zio.json.*
import zio.schema.{DeriveSchema, Schema}

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

type ID = UUID
object ID:
  def mkID: UUID = UUID.randomUUID()

@jsonHintNames(SnakeCase)
@jsonDiscriminator("type")
sealed trait Event:
  def eventID: ID

final case class NamespaceCreated(name: String, id: ID, eventID: ID = ID.mkID)              extends Event
final case class NamespaceRenamed(name: String, id: ID, eventID: ID = ID.mkID)              extends Event
final case class NamespaceDeleted(id: ID, eventID: ID = ID.mkID)                            extends Event
final case class EventCreated(
  id: ID,
  namespaceID: ID,
  name: String,
  dateTime: Option[LocalDateTime],
  tags: Set[String],
  eventID: ID = ID.mkID
) extends Event
final case class EventDeleted(id: ID, namespaceID: ID, eventID: ID = ID.mkID)               extends Event
final case class EventRenamed(id: ID, namespaceID: ID, name: String, eventID: ID = ID.mkID) extends Event

object Event:
  given schema: Schema[Event]                = DeriveSchema.gen[Event]
  given eventJsonEncoder: JsonEncoder[Event] = DeriveJsonEncoder.gen[Event]
  given eventJsonDecoder: JsonDecoder[Event] = DeriveJsonDecoder.gen[Event]

@jsonHintNames(SnakeCase)
@jsonDiscriminator("type")
sealed trait Command

final case class CreateNamespace(id: ID, name: String)              extends Command
final case class RenameNamespace(id: ID, name: String)              extends Command
final case class DeleteNamespace(id: ID)                            extends Command
final case class CreateEvent(
  namespaceID: ID,
  id: ID,
  name: String,
  dateTime: Option[LocalDateTime] = None,
  tags: Set[String] = Set.empty
) extends Command
final case class RenameEvent(namespaceID: ID, id: ID, name: String) extends Command
final case class DeleteEvent(namespaceID: ID, id: ID)               extends Command

trait Model:
  def updatedAt: LocalDateTime
  def createdAt: LocalDateTime
  def deletedAt: Option[LocalDateTime]

object Model:
  private val defaultZone        = ZoneId.systemDefault()
  private def now: LocalDateTime = LocalDateTime.now(defaultZone)

  final case class Namespace(
    id: ID,
    name: String,
    updatedAt: LocalDateTime,
    createdAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    events: List[Event] = List.empty
  ) extends Model

  object Namespace:
    import Event.given
    given schema: Schema[Namespace]                    = DeriveSchema.gen[Namespace]
    given namespaceJsonEncoder: JsonEncoder[Namespace] = DeriveJsonEncoder.gen[Namespace]

    def empty: Namespace =
      Namespace(ID.mkID, "", now, now, None)

  final case class Event(
    id: ID,
    name: String,
    updatedAt: LocalDateTime,
    createdAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    dateTime: Option[LocalDateTime] = None,
    tags: Set[String] = Set.empty
  ) extends Model

  object Event:
    given schema: Schema[Event]                = DeriveSchema.gen[Event]
    given eventJsonEncoder: JsonEncoder[Event] = DeriveJsonEncoder.gen[Event]

    def empty: Event =
      Event(ID.mkID, name = "", updatedAt = now, createdAt = now, deletedAt = None, dateTime = None, tags = Set.empty)
