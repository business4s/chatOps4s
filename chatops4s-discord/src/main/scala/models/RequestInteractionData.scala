package models

case class RequestInteractionData(
                                   `type`: Int,
                                   data: Option[RequestData],
                                   member: Option[RequestMember],
                                   channel_id: Option[String],
                                   message: Option[RequestMessage]
)
