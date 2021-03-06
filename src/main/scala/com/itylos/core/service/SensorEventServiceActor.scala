package com.itylos.core.service

import akka.actor.{Actor, ActorLogging, Props}
import com.itylos.core.dao.{SensorComponent, SensorEventComponent, SensorTypeComponent}
import com.itylos.core.domain.SensorEvent
import com.itylos.core.exception.{SensorDoesNotExistException, SensorTypeDoesNotExistException}
import com.itylos.core.rest.dto.SensorEventDto
import com.itylos.core.service.protocol._

/**
 * Companion object to properly initiate [[com.itylos.core.service.SensorEventServiceActor]]
 */
object SensorEventServiceActor {
  def props(): Props = {
    Props(new SensorEventServiceActor() with SensorEventComponent with SensorComponent
      with SensorTypeComponent with NotificationsHelper {
      val sensorDao = new SensorDao
      val sensorEventDao = new SensorEventDao
      val sensorTypeDao = new SensorTypeDao()
    })
  }
}

/**
 * An actor responsible for managing [[com.itylos.core.domain.SensorEvent]]
 */
class SensorEventServiceActor extends Actor with ActorLogging {
  this: SensorEventComponent with SensorComponent with SensorTypeComponent with NotificationsHelper =>


  def receive = {

    // --- Add sensor event --- //
    case AddSensorEventRq(sensorEvent) =>
      // Check sensor existence
      val sensorData = sensorDao.getSensorBySensorId(sensorEvent.sensorId)
      if (sensorData == None) throw new SensorDoesNotExistException(sensorEvent.sensorId)
      // Check sensor type
      val sensorType = sensorTypeDao.getSensorTypeByObjectId(sensorData.get.sensorTypeId)
      if (sensorType == None) throw new SensorTypeDoesNotExistException(sensorData.get.sensorTypeId)
      if (!sensorType.get.isBatteryPowered) sensorEvent.batteryLevel = -1
      sensorEventDao.save(sensorEvent)
      sender ! GetSensorEventsRs(List())
      notifyAll(context, NewSensorEventNotification(sensorData.get, sensorEvent))

    // --- Get sensor events --- //
    case GetSensorEventsRq(sensorId, limit, offset) =>
      val latestEvents = sensorEventDao.getSensorEvents(sensorId, limit, offset)
      sender ! GetSensorEventsRs(convert2DTOs(latestEvents))

    // --- Get latest event for each sensor --- //
    case GetSensorLatestEventsRq(sensorIds) =>
      val latestEvents = for (sensorId <- sensorIds) yield sensorEventDao.getLatestSensorEvent(sensorId)
      val rs = latestEvents.filter(le => le != None).map(le => le.get)
      sender ! GetSensorEventsRs(convert2DTOs(rs))

    // --- Remove sensor events associated to a sensor --- //
    case RemoveSensorEventsForSensor(sensorOId) =>
      sensorDao.checkSensorsExistenceByOid(List(sensorOId))
      val sensorId = sensorDao.getSensorByObjectId(sensorOId).get.sensorId
      sensorEventDao.removeEventsForSensor(sensorId)
      val latestEvents = sensorEventDao.getSensorEvents(None, 5, 0)
      sender ! GetSensorEventsRs(convert2DTOs(latestEvents))

  }

  /**
   * Convert [[com.itylos.core.domain.SensorEvent]] to [[com.itylos.core.rest.dto.SensorEventDto]]
   * @param sensorEvents the [[com.itylos.core.domain.SensorEvent]] instances to convert
   * @return the converted DTO objects
   */
  private def convert2DTOs(sensorEvents: List[SensorEvent]): List[SensorEventDto] = {
    for (sensorEvent <- sensorEvents)
      yield new SensorEventDto(sensorEvent, sensorDao.getSensorBySensorId(sensorEvent.sensorId).get)
  }


}