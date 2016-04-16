package com.frazier.bryce.vitalsigns

import net.liftweb.json._

/**
  * Created by bryce frazier on 4/10/16.
  */

import org.apache.spark.{SparkConf, SparkContext}

object VitalSigns {
  def main(args: Array[String]) {
    val inputFile = args(0)
    val patientRecordOutputFile = args(1)

    // read healthRecords from input file
    val healthRecords = Spark.ctx.textFile(inputFile).map(line => {
      implicit val formats = DefaultFormats
      parse(line.toString).extract[HealthRecord]
    })

    val bloodPressureResults = BloodPressureProcessor().obtainBloodPressureResults(healthRecords)
    val cholesterolResults = CholesterolProcessor().obtainCholesterolResults(healthRecords)
    val diabetesResults = DiabetesProcessor().obtainDiabetesResults(healthRecords)

    val healthResults = bloodPressureResults.union(cholesterolResults).union(diabetesResults)
    val transformer = Transformer()
    val resultsByRecordId = transformer.groupHealthResultsByRecordId(healthResults)

    val patientResults = transformer.obtainPatientResults(resultsByRecordId, healthRecords)

    val aggregatedResults = Aggregator().obtainAggregatedResults(patientResults)

    println(aggregatedResults)

    // save patientResults to output text file
    patientResults.map(result => {
      implicit val formats = DefaultFormats
      compact(render(Extraction.decompose(result)))
    }).saveAsTextFile(patientRecordOutputFile)
  }
}


object Spark {
  val sparkConf = new SparkConf().setAppName("VitalSigns").setMaster("local[1]")
  val ctx = new SparkContext(sparkConf)
}


// bloodPressure is represented as "118/74"
// in units: mm Hg, where the numerator is Systolic, and the denominator is Diastolic
// and read as "118 over 74 millimeters of mercury"


// cholesterol figures are in mg/dL (U.S. & other countries). Canada & most of Europe use mmol/L

//  glucoseLvl
//  # < 45 => Abnormally low
//  from  45 to 99 mg/DL (~3 to 5.5 mmol/L)  	Normal fasting glucose  //http://www.healthieryou.com/hypo.html
//    from 100 to 125 mg/DL(5.6 to 6.9 mmol/L)   	Prediabetes (impaired fasting glucose)
//  from 126 mg/DL(7.0 mmol/L) 				  Diabetes   (if only one => declare need to take another to be more decisive)
//  and above on more than one testing occasion			(logic counting if any labeled Diabetes)
//

case class Cholesterol(total: Int, ldl: Int, hdl: Int, triglycerides: Int)

case class HealthRecord(id: Long, patientId: Long, age: Long, gender: String, region: String, timestamp: Long, glucoseLvl: Long, cholesterol: Cholesterol, heartRate: Long, bloodPressure: String)

case class HealthResult(name: String, status: String, interpretation: String)

case class PatientResults(patientId: Long, recordId: Long, timestamp: Long, healthResults: List[HealthResult])

