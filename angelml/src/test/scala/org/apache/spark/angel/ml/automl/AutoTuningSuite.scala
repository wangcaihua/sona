package org.apache.spark.angel.ml.automl

import com.tencent.angel.sona.core.DriverContext
import com.tencent.angel.spark.automl.tuner.TunerParam
import com.tencent.angel.spark.automl.tuner.config.{Configuration, ConfigurationSpace}
import com.tencent.angel.spark.automl.tuner.solver.Solver
import org.apache.spark.angel.examples.AutoJsonRunnerExamples.parseConfig
import org.apache.spark.angel.ml.classification.AngelClassifier
import org.apache.spark.{SparkConf, SparkFunSuite}
import org.apache.spark.sql.{DataFrameReader, SparkSession}

import scala.collection.mutable

class AutoTuningSuite extends SparkFunSuite {
  private var spark: SparkSession = _
  private var libsvm: DataFrameReader = _
  private var dummy: DataFrameReader = _
  private var sparkConf: SparkConf = _
  private var driverCtx: DriverContext = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    spark = SparkSession.builder()
      .master("local[2]")
      .appName("AutoTuningAngelClassification")
      .getOrCreate()

    libsvm = spark.read.format("libsvmex")
    dummy = spark.read.format("dummy")
    sparkConf = spark.sparkContext.getConf
    driverCtx = DriverContext.get(sparkConf)

    driverCtx.startAngelAndPSAgent()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    driverCtx.stopAngelAndPSAgent()
  }

  test("fm_train") {

    val trainData = dummy.load("../data/angel/census/census_148d_train.dummy")

    val tuneIter = 20
    val minimize = false
    val surrogate = "GaussianProcess"
    val config = "learningRate|C|double|0.1:1:100#maxIter|D|float|10:50:5"

    // init solver
    val cs: ConfigurationSpace = new ConfigurationSpace("cs")
    implicit val solver: Solver = Solver(cs, minimize, surrogate)
    parseConfig(config)
    TunerParam.setBatchSize(1)

    // iterate tuning
    (0 until tuneIter).foreach { iter =>
      println(s"==========Tuner Iteration[$iter]==========")
      val configs: Array[Configuration] = solver.suggest
      for (config <- configs) {
        val paramMap: mutable.Map[String, Double] = new mutable.HashMap[String, Double]()
        for (paramType <- solver.getParamTypes) {
          paramMap += (paramType._1 -> config.get(paramType._1))
        }
        val numBatch = paramMap.getOrElse("numBatch", 10.toDouble).toInt
        val maxIter = paramMap.getOrElse("maxIter", 50.toDouble).toInt
        val lr = paramMap.getOrElse("learningRate", 0.1)
        val decayAlpha = paramMap.getOrElse("decayAlpha", 0.001)
        val decayBeta = paramMap.getOrElse("decayBeta", 0.001)
        val decayIntervals = paramMap.getOrElse("decayIntervals", 100.0).toInt

        val classifier = new AngelClassifier()
          .setModelJsonFile("../angelml/src/test/jsons/deepfm.json")
          .setNumClass(2)
          .setNumField(13)
          .setNumBatch(numBatch)
          .setMaxIter(maxIter)
          .setLearningRate(lr)
          .setDecayAlpha(decayAlpha)
          .setDecayBeta(decayBeta)
          .setDecayIntervals(decayIntervals)

        val model = classifier.fit(trainData)

        val metric = model.evaluate(trainData).accuracy
        solver.feed(config, metric)

        if ( (minimize && metric < solver.optimal._2)
          || (!minimize && metric > solver.optimal._2)) {
          println(s"find a better configuration = ${config.getVector.toArray.mkString("@")}, " +
            s"metric = $metric")
          model.write.overwrite().save("trained_models/fm")
        }
      }
    }
  }
}
