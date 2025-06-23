package api

import models.Interaction._

class InboundGateway(val interactions: Interactions) {
  private val baseEndpoint = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
      oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("bad request")),
    )
  )
  private val interactionsEndpoint = baseEndpoint
    .post
    .in("api" / "interactions")
    .in(header[String]("X-Signature-Ed25519"))
    .in(header[String]("X-Signature-Timestamp"))
    .in(stringBody)
    .out(jsonBody[InteractionResponse])
    .handle { case (signature, timestamp, body) =>
      val isValid = DiscordBot.verifySignature(
        signature,
        timestamp,
        body
      )
      if (!isValid) {
        Left(Unauthorized())
      } else {
        val decoded = decode[InteractionRequest](body)
        decoded match {
          case Right(interactionRequest) =>
            val interactionResponse = Chat4Ops.executeInteraction(
              interactionRequest = interactionRequest,
              interactions = interactions
            )
            if interactionResponse.isDefined then Right(interactionResponse.get) else Left(BadRequest())
          case Left(error) =>
            Left(BadRequest())
        }
      }
    }
  // Add shutdown hook to clean up server
  def start(): Unit = {
    val config = NettyConfig.default.withGracefulShutdownTimeout(2.seconds)
    val endpoints = List(this.acceptDeclineEndpoint, this.interactionsEndpoint, this.formEndpoint)
    val swaggerEndpoints = SwaggerInterpreter()
      .fromServerEndpoints[Identity](endpoints, "Chat4OpsServer", "1.0")
    NettySyncServer(config)
      .port(8080)
      .addEndpoints(endpoints)
      .addEndpoints(swaggerEndpoints)
      .startAndWait()
  }
}