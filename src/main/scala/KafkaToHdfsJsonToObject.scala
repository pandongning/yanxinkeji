import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 64273 pdn @create
  * 2018-12-25-11:49
  * kafkatohdfs
  *
  * 其会建立多个平行的目录
  */
object KafkaToHdfsJsonToObject {

  case class appclass(app_info_four: String)

  case class appInfoclass(app_info_one: String, app_info_two: String, app_info_there: String, app: appclass)

  case class hardWareInfoFourClass(cpuCurFreq: String, appVersion: String, isEmulator: String, Memory: String, cpuArchitecture: String, isRooted: String, appSignatures: String,
                                   BluetoothMAC: String, Build: String, NetworkType: String, ExternalStorage: String, ethIp: String, USB: String, appName: String,
                                   cpuSerial: String, ip: String, Time: String, AndroidId: String, cpuName: String, Siminformation: String, SysFeatures: String, cpuMaxFreq: String,
                                   appPackageName: String, Display: String, cpuMinFreq: String)

  case class hardwareInfoclass(hardware_info_one: String, hardware_info_two: String, hardware_info_there: String, hardware_info_four: hardWareInfoFourClass)

  case class dataclass(app_info: appInfoclass, hardware_info: hardwareInfoclass, uid: String, device_id: String)

  def main(args: Array[String]): Unit = {
    val sparkConf: SparkConf = new SparkConf().setAppName("kafka_riskDeviceFingerprin_toHdfs").setMaster("local[*]")
    val sparkContext = new SparkContext(sparkConf)
    sparkContext.setLogLevel("ERROR")
    val ssc = new StreamingContext(sparkContext, Seconds(10))


    val sourcetopic = "riskDeviceFingerprint"
    val toHdfs = "hdfs://192.168.25.10:9000/kafkaToSpark"

    //
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "192.168.25.10:9092,192.168.25.11:9092,192.168.25.12:9092",
      ConsumerConfig.GROUP_ID_CONFIG -> "first4",
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest"

    )


    var messages = KafkaUtils.createDirectStream[String, String](
      ssc, LocationStrategies.PreferConsistent, ConsumerStrategies.Subscribe[String, String](Array(sourcetopic), kafkaParams)
    )


    val values: DStream[String] = messages.map(_.value())

    val dataStreams = values.map(value => jsonToObject(value))



    /*  val device_id: DStream[String] = dataStreams.map(Data => Data.hardware_info.hardware_info_four.cpuCurFreq)
      device_id.print()
      dataStreams.print()*/


    /* val sqlContext = new org.apache.spark.sql.SQLContext(sparkContext)
     import sqlContext.implicits._

     dataStreams.foreachRDD(rddBatch => {
       rddBatch.toDF("app_info","hardware_info","uid","device_id").coalesce(1).write.mode(SaveMode.Append).save(toHdfs)
     })*/


    dataStreams.foreachRDD(rdd => {
     rdd.saveAsTextFile(toHdfs+System.currentTimeMillis())
    })

//    dataStreams.saveAsTextFiles(toHdfs, "txt")

    // Start the computation
    ssc.start()
    ssc.awaitTermination()

  }

  def jsonToObject(jsonString: String) = {
    val jsonMessage: JSONObject = JSON.parseObject(jsonString)
    val appInfoString: String = jsonMessage.getJSONObject("data").getString("app_info")
    val hardwareInfoString: String = jsonMessage.getJSONObject("data").getString("hardware_info")
    val uid: String = jsonMessage.getJSONObject("data").getString("uid")
    val device_id: String = jsonMessage.getJSONObject("data").getString("device_id")

    //    对app_info进行更加详细的解析
    val app_info_strings: Array[String] = appInfoString.split("\\|", 5)
    val app_info_one: String = app_info_strings(0)
    val app_info_two: String = app_info_strings(1)
    val app_info_there: String = app_info_strings(2)
    //    app_info的第3个字段又是一个Json串
    val app_info_four: String = app_info_strings(3)
    val app_info_four_app: String = JSON.parseObject(app_info_four).getString("app")


    //    对hardware_info的信息进行更加详细的解析
    val hardware_info_strings: Array[String] = hardwareInfoString.split("\\|", 5)
    val hardware_info_one: String = hardware_info_strings(0)
    val hardware_info_two: String = hardware_info_strings(1)
    val hardware_info_there: String = hardware_info_strings(2)
    val hardware_info_four: String = hardware_info_strings(3)


    val app = appclass(app_info_four_app)
    val app_info = appInfoclass(app_info_one, app_info_two, app_info_there, app)
    //    对应解析的是hardware_info_strings(3)字段
    var hardWareInfoFourClazz: hardWareInfoFourClass = JSON.parseObject(hardware_info_four, classOf[hardWareInfoFourClass])
    val hardware_info = hardwareInfoclass(hardware_info_one, hardware_info_two, hardware_info_there, hardWareInfoFourClazz)
    val data = dataclass(app_info, hardware_info, uid, device_id)

    data
  }

}











