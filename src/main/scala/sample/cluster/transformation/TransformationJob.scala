package sample.cluster.transformation

/**
 * Created by henry on 6/5/15.
 */
case class TransformationJob(text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration
