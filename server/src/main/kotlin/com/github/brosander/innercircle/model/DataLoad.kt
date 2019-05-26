package com.github.brosander.innercircle.model

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.stream.Collectors

data class Comment(val text: String, val user: String)
data class SharedWithUser(val user: String)
data class Post(
    val date: String,
    val images: List<String>,
    val videos: List<String>,
    val user: String,
    val text: String,
    val comments: List<Comment>,
    val shared_with: List<SharedWithUser>
)

data class User(val image: String, val name: String)
data class PostsFile(val posts: Map<String, Post>, val users: Map<String, User>)

fun main(args: Array<String>) {
    val dataloadJson = File(args[0])
    val scraperName = args[1]
    val scraperEmail = args[2]

    DataStore { it }.getConnection().use { connection ->
        connection.prepareStatement("drop table if exists comment").use { it.execute() }
        connection.prepareStatement("drop table if exists post_share").use { it.execute() }
        connection.prepareStatement("drop table if exists post_image").use { it.execute() }
        connection.prepareStatement("drop table if exists post_video").use { it.execute() }
        connection.prepareStatement("drop table if exists post").use { it.execute() }
        connection.prepareStatement("drop table if exists circle_member").use { it.execute() }
        connection.prepareStatement("drop table if exists circle").use { it.execute() }
        connection.prepareStatement("drop table if exists circle_user").use { it.execute() }
        connection.prepareStatement("drop table if exists image").use { it.execute() }
        connection.prepareStatement("drop table if exists video").use { it.execute() }

        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS image (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              location VARCHAR NOT NULL,
              source TEXT,
              PRIMARY KEY(id)
            )"""
        ).use { it.execute() }

        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS video (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              location VARCHAR NOT NULL,
              source TEXT,
              PRIMARY KEY(id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS circle_user (
              id INT GENERATED ALWAYS AS IDENTITY,
              name VARCHAR,
              email VARCHAR,
              image_id BIGINT REFERENCES image(id),
              source TEXT,
              PRIMARY KEY(id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS post (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              created_date DATE NOT NULL,
              user_id INT REFERENCES circle_user(id) NOT NULL,
              post_text VARCHAR,
              PRIMARY KEY(id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS circle (
              id INT GENERATED ALWAYS AS IDENTITY,
              owner_id INT REFERENCES circle_user(id),
              name VARCHAR,
              PRIMARY KEY(id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS circle_member (
              circle_id INT REFERENCES circle(id),
              user_id INT REFERENCES circle_user(id),
              PRIMARY KEY (circle_id, user_id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS post_share (
              post_id BIGINT REFERENCES post(id),
              circle_id INT REFERENCES circle(id),
              PRIMARY KEY (post_id, circle_id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS comment (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              post_id BIGINT REFERENCES post(id) NOT NULL,
              user_id INT REFERENCES circle_user(id) NOT NULL,
              comment_text VARCHAR NOT NULL,
              PRIMARY KEY (id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS post_image (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              post_id BIGINT REFERENCES post(id) NOT NULL,
              image_id BIGINT REFERENCES image(id) NOT NULL,
              PRIMARY KEY(post_id, image_id)
            )"""
        ).use { it.execute() }
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS post_video (
              id BIGINT GENERATED ALWAYS AS IDENTITY,
              post_id BIGINT REFERENCES post(id) NOT NULL,
              video_id BIGINT REFERENCES video(id) NOT NULL,
              PRIMARY KEY(post_id, video_id)
            )"""
        ).use { it.execute() }

        val posts: PostsFile = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(dataloadJson, PostsFile::class.java)


        connection.prepareStatement("")

        val usersSorted = posts.users.entries.stream()
            .sorted(Comparator.comparing { e: Map.Entry<String, User> -> e.value.name }.thenComparing { e: Map.Entry<String, User> -> e.key })
            .collect(Collectors.toList())

        val userImageIds: HashMap<String, Long> = HashMap()
        connection.prepareStatement("INSERT INTO image(location, source) VALUES (?, ?) RETURNING id").use {
            for (user in usersSorted) {
                it.setString(1, user.value.image)
                it.setString(2, user.key)

                it.executeQuery().use {
                    it.next()
                    userImageIds[user.key] = it.getLong(1)
                }
            }
        }
        val userIds: HashMap<String, Int> = HashMap()
        connection.prepareStatement("INSERT INTO circle_user(name, image_id, source) VALUES (?, ?, ?) RETURNING id")
            .use {
                for (entry in usersSorted) {
                    it.setString(1, entry.value.name)
                    it.setLong(2, userImageIds[entry.key]!!)
                    it.setString(3, entry.key)

                    it.executeQuery().use { rs ->
                        rs.next()
                        userIds[entry.key] = rs.getInt(1)
                    }
                }
            }

        connection.prepareStatement("UPDATE circle_user SET email = '$scraperEmail' WHERE name = '$scraperName'").use { it.execute() }

        val sdf = SimpleDateFormat("MMM dd, yyyy")
        val postSortedList = posts.posts.entries.stream().sequential()
            .sorted(Comparator.comparing { e: Map.Entry<String, Post> -> sdf.parse(e.value.date) })
            .collect(Collectors.toList())

        val postIds: HashMap<String, Long> = HashMap()
        for (entry in postSortedList) {
            connection.prepareStatement("INSERT INTO post(created_date, user_id, post_text) VALUES (?, ?, ?) RETURNING id")
                .use { stmt ->
                    stmt.setDate(1, Date(sdf.parse(entry.value.date).time))
                    stmt.setInt(2, userIds[entry.value.user]!!)
                    stmt.setString(3, entry.value.text)

                    stmt.executeQuery().use {
                        it.next()
                        postIds[entry.key] = it.getLong(1)
                    }
                }
        }

        for (entry in postSortedList) {
            connection.prepareStatement("INSERT INTO comment(post_id, user_id, comment_text) VALUES (?, ?, ?)")
                .use { stmt ->
                    for (comment in entry.value.comments) {
                        stmt.setLong(1, postIds[entry.key]!!)
                        stmt.setInt(2, userIds[comment.user]!!)
                        stmt.setString(3, comment.text)

                        stmt.execute()
                    }
                }
        }

        val imageIds: HashMap<String, Long> = HashMap()
        connection.prepareStatement("INSERT INTO image(location, source) VALUES (?, ?) RETURNING id").use {
            for (post in postSortedList) {
                for (image in post.value.images) {
                    it.setString(1, image)
                    it.setString(2, post.key)

                    it.executeQuery().use {
                        it.next()
                        imageIds[image] = it.getLong(1)
                    }
                }
            }
        }
        for (entry in postSortedList) {
            connection.prepareStatement("INSERT INTO post_image(post_id, image_id) VALUES (?, ?)").use { stmt ->
                for (image in entry.value.images) {
                    stmt.setLong(1, postIds[entry.key]!!)
                    stmt.setLong(2, imageIds[image]!!)

                    stmt.execute()
                }
            }
        }

        val videoIds: HashMap<String, Long> = HashMap()
        connection.prepareStatement("INSERT INTO video(location, source) VALUES (?, ?) RETURNING id").use {
            for (entry in postSortedList) {
                for (video in entry.value.videos) {
                    it.setString(1, video)
                    it.setString(2, entry.key)

                    it.executeQuery().use {
                        it.next()
                        videoIds[video] = it.getLong(1)
                    }
                }
            }
        }
        connection.prepareStatement("INSERT INTO post_video(post_id, video_id) VALUES (?, ?)").use {
            for (entry in postSortedList) {
                for (video in entry.value.videos) {
                    it.setLong(1, postIds[entry.key]!!)
                    it.setLong(2, videoIds[video]!!)

                    it.execute()
                }
            }
        }

        val circles: HashMap<Int, HashMap<Set<String>, Int>> = HashMap()
        connection.prepareStatement("INSERT INTO circle(owner_id, name) VALUES (?, ?) RETURNING id").use { stmt ->
            for (entry in postSortedList) {
                val sharedWith = entry.value.shared_with.stream().map { it.user }.collect(Collectors.toSet())
                val postUserId = userIds[entry.value.user]!!
                val userCircles = circles.computeIfAbsent(postUserId) { HashMap() }
                val maybeExistingCircle = userCircles[sharedWith]

                if (maybeExistingCircle == null) {
                    stmt.setInt(1, postUserId)
                    stmt.setString(2, "circle " + (userCircles.size + 1))

                    stmt.executeQuery().use {
                        it.next()
                        userCircles[sharedWith] = it.getInt(1)
                    }
                }
            }
        }

        connection.prepareStatement("INSERT INTO circle_member(circle_id, user_id) VALUES (?, ?)").use { stmt ->
            for (circle in circles) {
                for (userCircles in circle.value.entries) {
                    for (shared in userCircles.key) {
                        stmt.setInt(1, userCircles.value)
                        stmt.setInt(2, userIds[shared]!!)

                        stmt.execute()
                    }
                }
            }
        }

        val scraperId = connection.prepareStatement("SELECT id FROM circle_user WHERE name = '$scraperName'").use { it.executeQuery().use { it.next(); it.getInt(1) }}

        // Would've been shared with scraper or wouldn't have seen while scraping, user doesn't show in G+ list of shared_with
        val circleIds: List<Int> = connection.prepareStatement("SELECT id FROM circle WHERE owner_id != ?").use { stmt ->
            stmt.setInt(1, scraperId)

            stmt.executeQuery().use { rs ->
                val circleIds = ArrayList<Int>()
                while(rs.next()) {
                    circleIds.add(rs.getInt(1))
                }
                circleIds
            }
        }

        connection.prepareStatement("INSERT INTO circle_member(circle_id, user_id) VALUES (?, ?)").use { stmt ->
            for (circleId in circleIds) {
                stmt.setInt(1, circleId)
                stmt.setInt(2, scraperId)

                stmt.execute()
            }
        }

        connection.prepareStatement("INSERT INTO post_share(post_id, circle_id) VALUES (?, ?)").use { stmt ->
            for (entry in postSortedList) {
                val postUserId = userIds[entry.value.user]!!
                val sharedWith = entry.value.shared_with.stream().map { it.user }.collect(Collectors.toSet())
                stmt.setLong(1, postIds[entry.key]!!)
                stmt.setInt(2, circles[postUserId]!![sharedWith]!!)

                stmt.execute()
            }
        }
    }
}

