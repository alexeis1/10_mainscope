package ru.netology.nmedia.repository

import androidx.lifecycle.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okio.IOException
import ru.netology.nmedia.api.*
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.toDto
import ru.netology.nmedia.entity.toEntity
import ru.netology.nmedia.error.ApiError
import ru.netology.nmedia.error.NetworkError
import ru.netology.nmedia.error.UnknownError

class PostRepositoryImpl(private val dao: PostDao) : PostRepository {
    override val data = dao.getAll().map(List<PostEntity>::toDto)
    @Volatile private var lastLastNotPublishedId : Long = Long.MAX_VALUE / 2
        init {
            MainScope().launch {
                dao.getLastNotPublishedId().let {
                    if (it != 0L) lastLastNotPublishedId = it
                }
            }
        }

    override suspend fun getAll() {
        try {
            val response = PostsApi.service.getAll()
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body = response.body() ?: throw ApiError(response.code(), response.message())
            dao.insert(body.toEntity())
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override suspend fun save(post: Post) {
        try {
            val response = PostsApi.service.save(post)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body = response.body() ?: throw ApiError(response.code(), response.message())
            dao.insert(PostEntity.fromDto(body))
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override suspend fun removeById(id: Long) {
        try {
            //согласно условию задачи, сначала удаляем в БД
            dao.removeById(id)
            //потом с сервера
            val response = PostsApi.service.removeById(id)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override suspend fun likeById(id: Long) {
        try {
            data.value?.find { it.id == id }?.let {
                it.copy(
                    likedByMe = !it.likedByMe,
                    likes     =  it.likes + if (it.likedByMe) -1 else +1
                ).apply {
            //согласно условию задачи, сначала применяем в бд
                    dao.insert(PostEntity.fromDto(this))
            //потом на сервер
                    PostsApi.service.let {api->
                        if (likedByMe) api.likeById(id) else api.dislikeById(id)
                    }.apply {
                        if (!isSuccessful) {
                            throw ApiError(code(), message())
                        }
                    }
                }
            } ?: throw RuntimeException("UnknownError")
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }
}
