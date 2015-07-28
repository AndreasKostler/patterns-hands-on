//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package au.com.cba.omnia.patterns

import au.com.cba.omnia.eventually.core.ops.EventPatternQuerySyntax._
import au.com.cba.omnia.eventually.scalding.ops.EventPatternsSyntax._
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient

import org.joda.time.{Duration, DateTime}

import com.github.nscala_time.time.Implicits._

import shapeless.{CNil, :+:}

import com.twitter.scalding.{ Args, _}
import com.twitter.scalding.typed.{ TypedPipe, EmptyTypedPipe }

import au.com.cba.omnia.eventually.api
import api._
import au.com.cba.omnia.eventually.ferenginar.events._
import au.com.cba.omnia.eventually.api.entities.CIFPT
import au.com.cba.omnia.eventually.api.PartitionScheme.Unpartitioned

case class NetbankInteractionEvent(
                                    customer: CIFPT,
                                    logEntry: InstrumentationLogEntry
                                    ) extends HasSingleCustomer {
}

object NetbankInteractionEvent {
  def apply(customer: String, logEntry: InstrumentationLogEntry) =
    new NetbankInteractionEvent(CIFPT.create(customer.toLong), logEntry)

  implicit def netbankInteractionEventOrdering: Ordering[NetbankInteractionEvent] = Ordering.by(_.timestamp.getMillis)

  implicit def timestamp: Timestamped[NetbankInteractionEvent] = Timestamped.from(_.logEntry.timestamp)

}

class CountEventsJob(args: Args) extends EventExecutionJob[Unit](args) {

  override def execution: Execution[Unit] = {
    val metastore: HiveMetaStoreClient = implicitly[HiveMetaStoreClient]

    // The hive table underlying InstrumentationLogEntry is quite big so it
    // is wise to restrict processing to a (small) time interval
    val interval = new DateTime(2012, 11, 1, 0, 0) to new DateTime(2012, 11, 30, 23, 59)

    // These are the tables/events we are interested in...
    // ... the Commsee Interaction Events...
    val commseeInteractions = load[CommseeInteraction](Some(interval))

    // ... and the Netbank Instrumentation Log Entries
    val netbankLogs = load[InstrumentationLogEntry](Some(interval))


    // Instrumentation Log Entries aren't really events as they lack an entity.
    // We can create Netbank interaction events by joining Instrumentation Log Entries
    // with the  `ntb_clnt` table:

    // First we load the "ntb_clnt" table and extract the values we're interested in;
    // column 1 - 'ntp_client_id' and column 3 - 'cif_id'
    val ntp_client_id = 0
    val cif_id = 2

    val clientMapping: TypedPipe[(String, String)] = {
      val descr = HiveTable("default", "ntb_clnt", Unpartitioned)
      HiveHdfsStorage.fromMetastoreAsText(descr, metastore, LOG).map(
        _.load(None)
          .map(_ split '|')
          .map(row => (row(ntp_client_id), row(cif_id)))
      ).getOrElse(EmptyTypedPipe)
    }

    // The we join with the log entries and create NetbankInteractionEvent objects
    val netbankInteractions =
      netbankLogs
        .map(entry => (entry.loginName, entry))
        .join(clientMapping)
        .values
        .map { case (log, cifpt) =>
        NetbankInteractionEvent(cifpt, log)
      }


    type LogOrCommsee = NetbankInteractionEvent :+: CommseeInteraction :+: CNil

    val LogsAndCommsee = netbankInteractions.inject[LogOrCommsee] ++ commseeInteractions.inject[LogOrCommsee]

    LogsAndCommsee
      .groupByEntity
      .query(
        sequence[NetbankInteractionEvent, CommseeInteraction]
          .strict
          .within(Duration.standardMinutes(10L)))
      .values
      .filter { case (a, b) => true }
      .map { case  (a, b) => (a.customer, a.logEntry, b) }
      .writeExecution(TypedCsv(output / "pattern_matches"))

  }

}