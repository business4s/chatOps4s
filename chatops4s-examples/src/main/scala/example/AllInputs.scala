package example

import cats.effect.{IO, IOApp}
import chatops4s.slack.{ButtonClick, FormDef, FormSubmission, InitialValues, MessageId, SlackGateway, Url}
import chatops4s.slack.api.{ChannelId, ConversationId, Email, UserId}
import chatops4s.slack.api.blocks.{RichTextBlock, RichTextSection, RichTextText}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

import java.time.{Instant, LocalDate, LocalTime}

object AllInputs extends IOApp.Simple {

  private val token    = sys.env.getOrElse("SLACK_BOT_TOKEN", "xoxb-your-token")
  private val appToken = sys.env.getOrElse("SLACK_APP_TOKEN", "xapp-your-app-token")
  private val channel  = sys.env.getOrElse("SLACK_CHANNEL", "#testing-slack-app")

  case class AllInputsForm(
      text: Option[String],
      integer: Option[Int],
      decimal: Option[Double],
      checkbox: Boolean,
      email: Option[Email],
      url: Option[Url],
      date: Option[LocalDate],
      time: Option[LocalTime],
      datetime: Option[Instant],
      user: Option[UserId],
      users: Option[List[UserId]],
      channel: Option[ChannelId],
      channels: Option[List[ChannelId]],
      conversation: Option[ConversationId],
      conversations: Option[List[ConversationId]],
      richText: Option[RichTextBlock],
  ) derives FormDef

  private def prefilled(slack: SlackGateway[IO], click: ButtonClick[String]): IO[InitialValues[AllInputsForm]] =
    for {
      userInfo <- slack.getUserInfo(click.userId)
    } yield InitialValues.of[AllInputsForm]
      .set(_.text, Some("Hello world"))
      .set(_.integer, Some(42))
      .set(_.decimal, Some(3.14))
      .set(_.checkbox, true)
      .set(_.email, userInfo.profile.flatMap(_.email))
      .set(_.url, Some(Url("https://example.com")))
      .set(_.date, Some(LocalDate.of(2025, 6, 15)))
      .set(_.time, Some(LocalTime.of(14, 30)))
      .set(_.datetime, Some(Instant.ofEpochSecond(1700000000L)))
      .set(_.user, Some(click.userId))
      .set(_.users, Some(List(click.userId)))
      .set(_.channel, Some(click.messageId.channel))
      .set(_.channels, Some(List(click.messageId.channel)))
      .set(_.conversation, Some(ConversationId(click.messageId.channel.value)))
      .set(_.conversations, Some(List(ConversationId(click.messageId.channel.value))))
      .set(_.richText, Some(RichTextBlock(elements = List(
        RichTextSection(elements = List(RichTextText("Hello, this is prefilled rich text!"))),
      ))))

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack      <- SlackGateway.create(token, backend)
        msg        <- slack.send(channel, "Try all available form inputs:")
        form       <- slack.registerForm[AllInputsForm](onSubmit(slack, msg))
        openBtn    <- slack.registerButton[String] { click =>
                        slack.openForm(click.triggerId, form, "All Inputs")
                      }
        prefilledBtn <- slack.registerButton[String] { click =>
                          prefilled(slack, click).flatMap { iv =>
                            slack.openForm(click.triggerId, form, "All Inputs (Prefilled)", initialValues = iv)
                          }
                        }
        _          <- slack.update(
                        msg,
                        "Try all available form inputs:",
                        Seq(
                          openBtn.toButton("Open Form", "open"),
                          prefilledBtn.toButton("Open Prefilled", "prefilled"),
                        ),
                      )
        manifest   <- slack.manifest("AllInputsExample")
        _          <- IO.println(manifest)
        _          <- slack.listen(appToken)
      } yield ()
    }

  private def onSubmit(slack: SlackGateway[IO], parentMsg: MessageId)(submission: FormSubmission[AllInputsForm]): IO[Unit] = {
    val f = submission.values
    val lines = List(
      s"*Text:* ${f.text.getOrElse("_empty_")}",
      s"*Integer:* ${f.integer.map(_.toString).getOrElse("_empty_")}",
      s"*Decimal:* ${f.decimal.map(_.toString).getOrElse("_empty_")}",
      s"*Checkbox:* ${f.checkbox}",
      s"*Email:* ${f.email.map(_.value).getOrElse("_empty_")}",
      s"*URL:* ${f.url.map(_.value).getOrElse("_empty_")}",
      s"*Date:* ${f.date.map(_.toString).getOrElse("_empty_")}",
      s"*Time:* ${f.time.map(_.toString).getOrElse("_empty_")}",
      s"*Datetime:* ${f.datetime.map(_.toString).getOrElse("_empty_")}",
      s"*User:* ${f.user.map(id => s"<@${id.value}>").getOrElse("_empty_")}",
      s"*Users:* ${f.users.filter(_.nonEmpty).map(_.map(id => s"<@${id.value}>").mkString(", ")).getOrElse("_empty_")}",
      s"*Channel:* ${f.channel.map(id => s"<#${id.value}>").getOrElse("_empty_")}",
      s"*Channels:* ${f.channels.filter(_.nonEmpty).map(_.map(id => s"<#${id.value}>").mkString(", ")).getOrElse("_empty_")}",
      s"*Conversation:* ${f.conversation.map(_.value).getOrElse("_empty_")}",
      s"*Conversations:* ${f.conversations.filter(_.nonEmpty).map(_.map(_.value).mkString(", ")).getOrElse("_empty_")}",
      s"*Rich text:* ${f.richText.map(_.elements.mkString(", ")).getOrElse("_empty_")}",
    )
    val message = s"<@${submission.userId.value}> submitted:\n${lines.mkString("\n")}"
    slack.reply(parentMsg, message).void
  }
}
