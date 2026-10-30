package com.chatku.app.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @POST("api/auth/register") suspend fun register(@Body b: RegisterBody): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body b: LoginBody): AuthResponse

    @GET("api/me") suspend fun me(): User
    @PATCH("api/me") suspend fun updateMe(@Body b: UpdateMeBody): User
    @GET("api/users/search") suspend fun search(@Query("q") q: String): List<User>
    @GET("api/users/{id}") suspend fun user(@Path("id") id: String): User
    @POST("api/contacts/match") suspend fun match(@Body b: PhonesBody): List<User>

    @GET("api/chats") suspend fun chats(): List<Chat>
    @POST("api/chats/direct") suspend fun direct(@Body b: DirectBody): Chat
    @POST("api/chats/group") suspend fun group(@Body b: GroupBody): Chat
    @GET("api/chats/{id}") suspend fun chat(@Path("id") id: String): Chat
    @PATCH("api/chats/{id}") suspend fun updateChat(@Path("id") id: String, @Body b: UpdateChatBody): Chat
    @PATCH("api/chats/{id}/mute") suspend fun mute(@Path("id") id: String, @Body b: MuteBody): OkResponse
    @POST("api/chats/{id}/members") suspend fun addMembers(@Path("id") id: String, @Body b: AddMembersBody): Chat
    @DELETE("api/chats/{id}/members/{userId}") suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): OkResponse

    @GET("api/chats/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("before_id") beforeId: String?,
        @Query("limit") limit: Int = 50,
    ): MessagesPage

    @POST("api/chats/{id}/messages") suspend fun send(@Path("id") id: String, @Body b: SendBody): Message
    @POST("api/chats/{id}/read") suspend fun markRead(@Path("id") id: String): OkResponse

    @PATCH("api/messages/{id}") suspend fun edit(@Path("id") id: String, @Body b: EditBody): Message
    @DELETE("api/messages/{id}") suspend fun delete(@Path("id") id: String): OkResponse
    @PUT("api/messages/{id}/reactions") suspend fun react(@Path("id") id: String, @Body b: EmojiBody): OkResponse
    @DELETE("api/messages/{id}/reactions") suspend fun unreact(@Path("id") id: String): OkResponse
    @GET("api/messages/{id}/receipts") suspend fun receipts(@Path("id") id: String): List<Receipt>

    @Multipart
    @POST("api/media")
    suspend fun upload(@Part file: MultipartBody.Part): MediaInfo

    @GET("api/channels") suspend fun channels(@Query("q") query: String? = null): List<Channel>
    @POST("api/channels") suspend fun createChannel(@Body b: CreateChannelBody): Channel
    @POST("api/channels/{id}/follow") suspend fun followChannel(@Path("id") id: String): OkResponse
    @DELETE("api/channels/{id}/follow") suspend fun unfollowChannel(@Path("id") id: String): OkResponse
    @GET("api/channels/{id}/posts") suspend fun channelPosts(@Path("id") id: String): List<ChannelPost>
    @POST("api/channels/{id}/posts") suspend fun createChannelPost(@Path("id") id: String, @Body b: CreateChannelPostBody): ChannelPost
    @GET("api/statuses") suspend fun statuses(): List<Status>
    @POST("api/statuses") suspend fun createStatus(@Body b: CreateStatusBody): Status
    @POST("api/statuses/{id}/view") suspend fun viewStatus(@Path("id") id: String): OkResponse
}
