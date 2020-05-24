package com.github.brosander.innercircle.model

import com.github.brosander.innercircle.model.connection.ConnectionFactory
import com.github.brosander.innercircle.services.files.FileResolver
import java.sql.*
import java.sql.Date
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.IntStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class UserListUser(val id: Int, val name: String, val email: String?)
data class InnerCircleSession(val name: String, val email: String?, val userId: Int, val generated: Long?, val expiration: Long)
data class UserMinimal(val id: Int, val name: String, val image: String?)
data class PostComment(val text: String, val user: UserMinimal)
data class PostImage(val id: Long, val location: String, val source: String, val thumbnail: String)
data class PostVideo(val id: Long, val location: String, val source: String, val thumbnail: String)
data class PostListEntry(
    val id: Long,
    val text: String,
    val createdDate: Date,
    val user: UserMinimal,
    val comments: List<PostComment>,
    val images: List<PostImage>,
    val videos: List<PostVideo>
)
data class Circle(val id: Int, val name: String)

class DataStore @Inject @Singleton constructor(private val resolver: FileResolver, private val connectionFactory: ConnectionFactory) {
    private val numPosts = 20
    private val numPostsQuestionMarks = IntStream.range(0, numPosts).mapToObj { "?" }.collect(Collectors.joining(", "))

    fun getSessionForEmail(email: String): InnerCircleSession? {
        connectionFactory.getConnection().use { connection ->
            connection.prepareStatement("SELECT name, id FROM circle_user WHERE email = ?").use { stmt ->
                stmt.setString(1, email)

                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val now = System.currentTimeMillis()
                        return InnerCircleSession(rs.getString(1), email, rs.getInt(2), now, now + TimeUnit.DAYS.toMillis(14))
                    }
                    return null
                }
            }
        }
    }

    fun getSessionForId(id: Int): InnerCircleSession? {
        connectionFactory.getConnection().use { connection ->
            connection.prepareStatement("SELECT name, email FROM circle_user WHERE id = ?").use { stmt ->
                stmt.setInt(1, id)

                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val now = System.currentTimeMillis()
                        return InnerCircleSession(rs.getString(1), rs.getString(2), id, now, now + TimeUnit.DAYS.toMillis(14))
                    }
                    return null
                }
            }
        }
    }

    fun listCircles(userId: Int): List<Circle> {
        return connectionFactory.getConnection().use { connection ->
            connection.prepareStatement("SELECT id, name FROM circle WHERE owner_id = ?").use { stmt ->
                stmt.setInt(1, userId)

                stmt.executeQuery().use {
                    val result = ArrayList<Circle>()
                    while (it.next()) {
                        result.add(Circle(it.getInt(1), it.getString(2)))
                    }
                    result
                }
            }
        }
    }

    fun listUsers(): List<UserListUser> {
        connectionFactory.getConnection().use { connection ->
            connection.prepareStatement("SELECT name, id, email FROM circle_user ORDER BY name, email, id").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = ArrayList<UserListUser>()
                    while (rs.next()) {
                        result.add(UserListUser(rs.getInt(2), rs.getString(1), rs.getString(3)))
                    }
                    return result
                }
            }
        }
    }

    fun createPost(userId: Int, text: String): Long {
        return connectionFactory.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO post(created_date, user_id, post_text) VALUES (?, ?, ?) RETURNING id").use { stmt ->
                stmt.setDate(1, Date(System.currentTimeMillis()))
                stmt.setInt(2, userId)
                stmt.setString(3, text)

                stmt.executeQuery().use {
                    it.next()
                    it.getLong(1)
                }
            }
        }
    }

    private val listPostsQuery = """
    SELECT
      post.id,
      post.post_text,
      post.created_date,
      circle_user.id as post_user_id,
      circle_user.name as post_user_name,
      image.location as post_user_image
    FROM post
      INNER JOIN circle_user ON circle_user.id = post.user_id
      LEFT JOIN image ON circle_user.image_id = image.id
    WHERE
      post.user_id = ? OR ? in (
            SELECT circle_member.user_id
            FROM post_share
              INNER JOIN circle_member ON circle_member.circle_id = post_share.circle_id
            WHERE
              post_share.post_id = post.id
        )
    ORDER BY post.created_date DESC, post.id DESC limit ?
    """

    private val listPostsBeforeQuery = """
    SELECT
      post.id,
      post.post_text,
      post.created_date,
      circle_user.id as post_user_id,
      circle_user.name as post_user_name,
      image.location as post_user_image
    FROM post
      INNER JOIN circle_user ON circle_user.id = post.user_id
      LEFT JOIN image ON circle_user.image_id = image.id
    WHERE
      (post.user_id = ? OR ? in (
            SELECT circle_member.user_id
            FROM post_share
              INNER JOIN circle_member ON circle_member.circle_id = post_share.circle_id
            WHERE
              post_share.post_id = post.id
        ))
      AND post.id < ?
    ORDER BY post.created_date DESC, post.id DESC limit ?
    """

    private val listPostCommentsQuery = """
    SELECT
      post_comment.post_id,
      post_comment.comment_text,
      circle_user.id,
      circle_user.name,
      image.location
    FROM (
       SELECT
        ROW_NUMBER() OVER (PARTITION BY post_id ORDER BY id DESC) as r,
        comment.id,
        comment.post_id,
        comment.comment_text as comment_text,
        comment.user_id as user_id
      FROM comment) as post_comment
      INNER JOIN circle_user on circle_user.id = post_comment.user_id
      LEFT JOIN image ON circle_user.image_id = image.id
    WHERE
      post_comment.r < 4 AND
      post_comment.post_id in (""" + numPostsQuestionMarks + """)
    ORDER BY post_comment.id
    """

    private val listPostImagesQuery = """
    SELECT
      post_image.post_id,
      post_image.image_id,
      image.location,
      image.source
    FROM post_image
      INNER JOIN image on image.id = post_image.image_id
    WHERE
      post_image.post_id in (""" + numPostsQuestionMarks + """)
    ORDER BY post_image.id
    """

    private val listPostVideosQuery = """
    SELECT
      post_video.post_id,
      post_video.video_id,
      video.location,
      video.source
    FROM post_video
      INNER JOIN video on video.id = post_video.video_id
    WHERE
      post_video.post_id in (""" + numPostsQuestionMarks + """)
    ORDER BY post_video.id
    """

    fun listPosts(userId: Int, beforeId: Long? = null): List<PostListEntry> {
        if (beforeId != null) {
            return listPosts {
                val stmt = it.prepareStatement(listPostsBeforeQuery)
                stmt.setInt(1, userId)
                stmt.setInt(2, userId)
                stmt.setLong(3, beforeId)
                stmt.setInt(4, numPosts)
                stmt
            }
        } else {
            return listPosts {
                val stmt = it.prepareStatement(listPostsQuery)
                stmt.setInt(1, userId)
                stmt.setInt(2, userId)
                stmt.setInt(3, numPosts)
                stmt
            }
        }
    }

    private fun listPosts(statementCreator: (Connection) -> PreparedStatement): List<PostListEntry> {
        val result = ArrayList<PostListEntry>()
        val commentLists = HashMap<Long, ArrayList<PostComment>>()
        val imageLists = HashMap<Long, ArrayList<PostImage>>()
        val videoLists = HashMap<Long, ArrayList<PostVideo>>()

        connectionFactory.getConnection().use { connection ->
            statementCreator.invoke(connection).use { stmt ->
                stmt.executeQuery().use {
                    while (it.next()) {
                        var i = 1
                        val id = it.getLong(i++)
                        val comments = ArrayList<PostComment>()
                        val images = ArrayList<PostImage>()
                        val videos = ArrayList<PostVideo>()
                        result.add(
                            PostListEntry(
                                id,
                                it.getString(i++),
                                it.getDate(i++),
                                UserMinimal(
                                        id = it.getInt(i++),
                                        name = it.getString(i++),
                                        image = it.getString(i)?.let { image -> resolver.resolve(image) }
                                ),
                                comments,
                                images,
                                videos
                            )
                        )
                        commentLists[id] = comments
                        imageLists[id] = images
                        videoLists[id] = videos
                    }
                    if (result.size == 0) {
                        return Collections.emptyList()
                    }
                }
            }
        }

        doPostListSecondaryQuery(listPostCommentsQuery, result) {
            var i = 1
            // In clause only lets us get comments for posts in our map already
            commentLists[it.getLong(i++)]!!.add(
                PostComment(
                    it.getString(i++),
                    UserMinimal(it.getInt(i++), it.getString(i++), resolver.resolve(it.getString(i)))
                )
            )
        }
        doPostListSecondaryQuery(listPostImagesQuery, result) {
            var i = 1
            // In clause only lets us get comments for posts in our map already
            val postId = it.getLong(i++)
            val id = it.getLong(i++)
            val location = it.getString(i++)
            val source = it.getString(i)
            imageLists[postId]!!.add(PostImage(id, resolver.resolve(location), source, resolver.resolve("$location.thumbnail.jpg")))
        }
        doPostListSecondaryQuery(listPostVideosQuery, result) {
            var i = 1
            // In clause only lets us get comments for posts in our map already
            val postId = it.getLong(i++)
            val id = it.getLong(i++)
            val location = it.getString(i++)
            val source = it.getString(i)
            videoLists[postId]!!.add(PostVideo(id, resolver.resolve(location), source, resolver.resolve("$location.thumbnail.jpg")))
        }
        return result
    }

    private fun doPostListSecondaryQuery(query: String, result: List<PostListEntry>, rowParser: (ResultSet) -> Unit) {
        val resultSize = result.size

        connectionFactory.getConnection().use { connection ->
            connection.prepareStatement(query).use { stmt ->
                for (i in 1..numPosts) {
                    if (i >= resultSize) {
                        stmt.setLong(i, result[0].id)
                    } else {
                        stmt.setLong(i, result[i].id)
                    }
                }
                stmt.executeQuery().use {
                    while (it.next()) {
                        rowParser.invoke(it)
                    }
                }
            }
        }
    }

    private val checkProfileAccessQuery = """
    SELECT
      1
    FROM circle_user
      INNER JOIN image ON circle_user.image_id = image.id
    WHERE
      image.location = ?
      AND
      (circle_user.id = ? OR ? in (
            SELECT circle_member.user_id
            FROM post_share
              INNER JOIN circle_member ON circle_member.circle_id = post_share.circle_id
        ))
    """

    private val checkImageAccessQuery = """
    SELECT
      1
    FROM post
      INNER JOIN circle_user ON post.user_id = circle_user.id
      LEFT JOIN post_image ON post_image.post_id = post.id
      LEFT JOIN image ON post_image.image_id = image.id
    WHERE
      image.location = ?
      AND
      (post.user_id = ? OR ? in (
            SELECT circle_member.user_id
            FROM post_share
              INNER JOIN circle_member ON circle_member.circle_id = post_share.circle_id
            WHERE
              post_share.post_id = post.id
        ))
    """

    private val checkVideoAccessQuery = """
    SELECT
      1
    FROM post
      INNER JOIN circle_user ON post.user_id = circle_user.id
      LEFT JOIN post_video ON post_video.post_id = post.id
      LEFT JOIN video ON post_video.video_id = video.id
    WHERE
      video.location = ?
      AND
      (post.user_id = ? OR ? in (
            SELECT circle_member.user_id
            FROM post_share
              INNER JOIN circle_member ON circle_member.circle_id = post_share.circle_id
            WHERE
              post_share.post_id = post.id
        ))
    """

    fun checkFileAccess(userId: Int, location: String): Boolean {
        if (location.endsWith("ProfilePicture.jpg")) {
            return connectionFactory.getConnection().use { connection ->
                connection.prepareStatement(checkProfileAccessQuery).use { stmt ->
                    stmt.setString(1, location)
                    stmt.setInt(2, userId)
                    stmt.setInt(3, userId)

                    stmt.executeQuery().use { it.next() }
                }
            }
        } else  if (location.endsWith(".mp4")) {
            return connectionFactory.getConnection().use { connection ->
                connection.prepareStatement(checkVideoAccessQuery).use { stmt ->
                    stmt.setString(1, location)
                    stmt.setInt(2, userId)
                    stmt.setInt(3, userId)

                    stmt.executeQuery().use { it.next() }
                }
            }
        } else {
            return connectionFactory.getConnection().use { connection ->
                connection.prepareStatement(checkImageAccessQuery).use { stmt ->
                    stmt.setString(1, location)
                    stmt.setInt(2, userId)
                    stmt.setInt(3, userId)

                    stmt.executeQuery().use { it.next() }
                }
            }
        }
    }
}