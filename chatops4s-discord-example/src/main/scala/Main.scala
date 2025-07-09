import cats.effect.IOApp
import sttp.tapir.endpoint


object Main extends IOApp:
  private val discordInbound = new DiscordInbound()

  private val sendEndpoint = endpoint.get
    .in("send")
    .out(jsonBody[MessageResponse])

  private val sendRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      sendEndpoint.serverLogicSuccess[IO](_ => {
        val discordOutbound = new DiscordOutbound(
          token = EnvLoader.get("DISCORD_BOT_TOKEN"),
          url = EnvLoader.get("DISCORD_BOT_URL"),
          applicationId = EnvLoader.get("DISCORD_BOT_APPLICATION_ID")
        )
        val acceptButton = discordInbound.registerAction((ctx) =>
          discordOutbound.sendToChannel(
            ctx.channelId,
            Message(
              text = "You pressed accept!"
            )
          ).flatMap { response =>
            IO.println(s"Accepted. Sent message ${response.messageId}")
          }
        )
        val declineButton = discordInbound.registerAction((ctx) =>
          discordOutbound.sendToChannel(
            ctx.channelId,
            Message(
              text = "You pressed decline!"
            )
          ).flatMap { response =>
            IO.println(s"Declined. Sent message ${response.messageId}")
          }
        )
        val message = Message(
          text = "Deploy to production?",
          interactions = Seq(
            acceptButton.render("Accept"),
            declineButton.render("Decline")
          )
        )
        val response = discordOutbound.sendToChannel("1381992880834351184", message)
        response
      })
    )


  private val swaggerRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](
        List(interactionEndpoint, sendEndpoint),
        "Discord Interaction API",
        "1.0"
      )
    )

  override def run(args: List[String]): IO[ExitCode] = {
    EnvLoader.loadEnv()

    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(interactionEndpoint.serverLogic[IO] {
          case (signature, timestamp, json) =>
            val discordPublicKey = "cec2f053ddcba6bb67570ac176afc730df3325a729ccb32edbed9dbe4d1741ca"
            if (!verifySignature(discordPublicKey, signature, timestamp, json)) {
              IO.pure(Left(Unauthorized()))
            } else {
              parse(json) match {
                case Right(json) => logic(signature, timestamp, json)
                case Left(err) => IO.pure(Left(BadRequest(what = s"Parsing error: ${err.message}")))
              }
            }
        })

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router(
        "/" -> (routes <+> swaggerRoutes <+> sendRoutes) // Combine all routes here
      ).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }