package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ShelfBookSummary
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.model.PrivateBookFacts
import io.legado.app.help.MareaSpace
import io.legado.app.help.book.isNotShelf
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class GroupBookCount(
    val groupId: Long,
    val count: Int
)

private const val PRIVATE_GROUP_MASK =
    "(SELECT COALESCE(SUM(groupId), 0) FROM book_groups WHERE groupId > 0 AND isPrivate = 1)"

private const val PUBLIC_GROUP_MASK =
    "(SELECT COALESCE(SUM(groupId), 0) FROM book_groups WHERE groupId > 0 AND isPrivate = 0)"

private const val PUBLIC_BOOK_FILTER =
    "(`group` = 0 OR (`group` & $PRIVATE_GROUP_MASK) = 0)"

// exp.10：书架面查询（分组列表 / 搜索 / 分组计数 / 分组预览）一律带 `space = :space`
// 谓词按 Marea 当前空间过滤，空间值由分发方法与 Repository 层经
// MareaSpace.spaceFlow.flatMapLatest 注入，切换空间时正在收集的流自动重查。
// 按 bookUrl 的单本读写、后台更新检查、Legado 自身私密书判定不走这条过滤。

@Dao
interface BookDao {

    @Query("UPDATE books SET disableReadRecord = :disabled WHERE bookUrl = :bookUrl")
    fun setReadRecordDisabled(bookUrl: String, disabled: Boolean)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flowByGroup(groupId: Long): Flow<List<Book>> {
        return MareaSpace.spaceFlow.flatMapLatest { space ->
            when (groupId) {
                BookGroup.IdRoot -> flowRoot(space)
                BookGroup.IdAll -> flowAll(space)
                BookGroup.IdLocal -> flowLocal(space)
                BookGroup.IdAudio -> flowAudio(space)
                BookGroup.IdNetNone -> flowNetNoGroup(space)
                BookGroup.IdLocalNone -> flowLocalNoGroup(space)
                BookGroup.IdManga -> flowManga(space)
                BookGroup.IdText -> flowText(space)
                BookGroup.IdError -> flowUpdateError(space)
                BookGroup.IdUnread -> flowUnread(space)
                BookGroup.IdReading -> flowReading(space)
                BookGroup.IdReadFinished -> flowReadFinished(space)
                BookGroup.IdReadFinishedUpdate -> flowReadFinishedUpdate(space)
                BookGroup.IdReadFinishedComplete -> flowReadFinishedComplete(space)
                else -> flowByUserGroup(groupId, space)
            }
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return MareaSpace.spaceFlow.flatMapLatest { space ->
            when (groupId) {
                BookGroup.IdRoot -> flowBookShelfRoot(space)
                BookGroup.IdAll -> flowBookShelf(space)
                BookGroup.IdLocal -> flowBookShelfLocal(space)
                BookGroup.IdAudio -> flowBookShelfAudio(space)
                BookGroup.IdNetNone -> flowBookShelfNetNoGroup(space)
                BookGroup.IdLocalNone -> flowBookShelfLocalNoGroup(space)
                BookGroup.IdManga -> flowBookShelfManga(space)
                BookGroup.IdText -> flowBookShelfText(space)
                BookGroup.IdError -> flowBookShelfUpdateError(space)
                BookGroup.IdUnread -> flowBookShelfUnread(space)
                BookGroup.IdReading -> flowBookShelfReading(space)
                BookGroup.IdReadFinished -> flowBookShelfReadFinished(space)
                BookGroup.IdReadFinishedUpdate -> flowBookShelfReadFinishedUpdate(space)
                BookGroup.IdReadFinishedComplete -> flowBookShelfReadFinishedComplete(space)
                else -> flowBookShelfByUserGroup(groupId, space)
            }
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    @Query(
        """
        select * from books where type & ${BookType.text} > 0
        and type & ${BookType.local} = 0
        and ($PUBLIC_GROUP_MASK & `group`) = 0
        and $PUBLIC_BOOK_FILTER
        AND space = :space
        and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        """
    )
    fun flowRoot(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        where type & ${BookType.text} > 0
        and type & ${BookType.local} = 0
        and ($PUBLIC_GROUP_MASK & `group`) = 0
        and $PUBLIC_BOOK_FILTER
        AND space = :space
        and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        """
    )
    fun flowBookShelfRoot(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space order by durChapterTime desc")
    fun flowAll(space: Int): Flow<List<Book>>

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<Book>

    @Query(
        """
    SELECT 
        bookUrl,
        name,
        author,
        origin,
        originName,
        coverUrl,
        customCoverUrl,
        durChapterTitle,
        durChapterTime,
        durChapterPos,
        latestChapterTitle,
        latestChapterTime,
        lastCheckCount,
        totalChapterNum,
        durChapterIndex,
        type,
        `group`,
        `order`,
        canUpdate,
        ifnull(customIntro, ifnull(listIntro, intro)) as intro,
        kind,
        customTag,
        wordCount
    FROM books
    WHERE $PUBLIC_BOOK_FILTER
    AND space = :space
    ORDER BY durChapterTime DESC
"""
    )
    fun flowBookShelf(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space AND type & ${BookType.audio} > 0")
    fun flowAudio(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books
        WHERE type & ${BookType.audio} > 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfAudio(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space AND type & ${BookType.local} > 0")
    fun flowLocal(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE type & ${BookType.local} > 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfLocal(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        select * from books where space = :space AND type & ${BookType.audio} = 0 and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowNetNoGroup(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        where type & ${BookType.audio} = 0 and type & ${BookType.local} = 0
        and ($PUBLIC_GROUP_MASK & `group`) = 0
        and $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfNetNoGroup(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        select * from books where space = :space AND type & ${BookType.local} > 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowLocalNoGroup(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        where type & ${BookType.local} > 0
        and ($PUBLIC_GROUP_MASK & `group`) = 0
        and $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfLocalNoGroup(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space AND (`group` & :group) > 0")
    fun flowByUserGroup(group: Long, space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE (`group` & :group) > 0
        AND ((SELECT isPrivate FROM book_groups WHERE groupId = :group) = 1 OR $PUBLIC_BOOK_FILTER)
        AND space = :space
        """
    )
    fun flowBookShelfByUserGroup(group: Long, space: Int): Flow<List<BookShelfItem>>

    @Query(
        "SELECT * FROM books WHERE space = :space AND (name like '%'||:key||'%' or author like '%'||:key||'%' or originName like '%'||:key||'%')"
    )
    fun flowSearch(key: String, space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE (name like '%'||:key||'%' or author like '%'||:key||'%' or originName like '%'||:key||'%'
            or kind like '%'||:key||'%' or customTag like '%'||:key||'%')
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfSearch(key: String, space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books where space = :space AND type & ${BookType.updateError} > 0 order by durChapterTime desc")
    fun flowUpdateError(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        where type & ${BookType.updateError} > 0 
        and $PUBLIC_BOOK_FILTER
        AND space = :space
        order by durChapterTime desc
        """
    )
    fun flowBookShelfUpdateError(space: Int): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE space = :space AND durChapterIndex = 0 AND durChapterPos = 0""")
    fun flowUnread(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE durChapterIndex = 0 AND durChapterPos = 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfUnread(space: Int): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE space = :space AND totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1""")
    fun flowReadFinished(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfReadFinished(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """SELECT * FROM books WHERE space = :space AND totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 1"""
    )
    fun flowReadFinishedUpdate(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 1
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfReadFinishedUpdate(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """SELECT * FROM books WHERE space = :space AND totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 0"""
    )
    fun flowReadFinishedComplete(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfReadFinishedComplete(space: Int): Flow<List<BookShelfItem>>

    @Query("""SELECT * FROM books WHERE space = :space AND totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1""")
    fun flowReading(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfReading(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space AND type & ${BookType.image} > 0")
    fun flowManga(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE type & ${BookType.image} > 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfManga(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE space = :space AND type & ${BookType.text} > 0")
    fun flowText(space: Int): Flow<List<Book>>

    @Query(
        """
        SELECT 
            bookUrl,
            name,
            author,
            origin,
            originName,
            coverUrl,
            customCoverUrl,
            durChapterTitle,
            durChapterTime,
            durChapterPos,
            latestChapterTitle,
            latestChapterTime,
            lastCheckCount,
            totalChapterNum,
            durChapterIndex,
            type,
            `group`,
            `order`,
            canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro,
            kind,
            customTag,
            wordCount
        FROM books 
        WHERE type & ${BookType.text} > 0
        AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowBookShelfText(space: Int): Flow<List<BookShelfItem>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun getBooksByGroup(group: Long): List<Book>

    @Query("SELECT * FROM books WHERE `name` in (:names)")
    fun findByName(vararg names: String): List<Book>

    @Query("select * from books where originName = :fileName")
    fun getBookByFileName(fileName: String): Book?

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?

    /**
     * 按 bookUrl 批量取书，供跨分组多选解析选中实体（选中项可能不在当前分组列表里）。
     * 调用方负责分批，避免单次绑定参数超过 SQLite 上限。
     */
    @Query("SELECT * FROM books WHERE bookUrl IN (:bookUrls)")
    suspend fun getBooksByUrls(bookUrls: Set<String>): List<Book>

    @Query(
        """
        SELECT
            bookUrl,
            type & ${BookType.local} > 0 AS isLocal,
            type & ${BookType.audio} > 0 AS isAudio,
            durChapterIndex,
            totalChapterNum - 1 AS lastChapterIndex
        FROM books
        WHERE bookUrl IN (:bookUrls)
        """
    )
    fun getCacheableBooks(bookUrls: Set<String>): List<CacheableBook>

    /**
     * 书架作品的轻量快照，供加入书架查重使用。
     *
     * 这是一次全表行扫描（未下架的作品），省掉的只是 `intro` / `variable` 等重字段，不是扫描行数。
     * 规范化后的重名判定（全角半角、空白折叠）必须在 Kotlin 侧做，因此无法再用 `name` 等值条件
     * 预筛；书架量级下这个代价可以接受，真要到需要预筛的规模，应先把规范化结果落成持久列。
     */
    @Query(
        """
        SELECT bookUrl, name, author, coverUrl, customCoverUrl, origin, originName,
               totalChapterNum, latestChapterTitle, durChapterTime
        FROM books
        WHERE type & ${BookType.notShelf} = 0
        """
    )
    fun getShelfBookSummaries(): List<ShelfBookSummary>

    /**
     * books 表的轻量失效信号（含未上架的书）。
     *
     * 只用于让依赖「书架里有几本同名作品」的 Flow 能在书架增删、改名时重新计算，
     * 调用方不关心具体数值。
     */
    @Query("SELECT COUNT(*) FROM books")
    fun flowBookCount(): Flow<Int>

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun flowGetBook(bookUrl: String): Flow<Book?>

    @Query("SELECT * FROM books WHERE name = :name and author = :author")
    fun getBook(name: String, author: String): Book?

    @Query("""select distinct bs.* from books, book_sources bs 
        where origin == bookSourceUrl and origin not like '${BookType.localTag}%' 
        and origin not like '${BookType.webDavTag}%'""")
    fun getAllUseBookSource(): List<BookSource>

    @Query("SELECT * FROM books WHERE name = :name and origin = :origin")
    fun getBookByOrigin(name: String, origin: String): Book?

    @get:Query("select count(bookUrl) from books where (SELECT sum(groupId) FROM book_groups)")
    val noGroupSize: Int

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0")
    val webBooks: List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0 and canUpdate = 1")
    val hasUpdateBooks: List<Book>

    @get:Query("SELECT * FROM books")
    val all: List<Book>

    @Query("SELECT * FROM books where type & :type > 0 and type & ${BookType.local} = 0")
    fun getByTypeOnLine(type: Int): List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.text} > 0 ORDER BY durChapterTime DESC limit 1")
    val lastReadBook: Book?

    @get:Query("SELECT bookUrl FROM books")
    val allBookUrls: List<String>

    @get:Query("SELECT COUNT(*) FROM books")
    val allBookCount: Int

    @get:Query("select min(`order`) from books")
    val minOrder: Int

    @get:Query("select max(`order`) from books")
    val maxOrder: Int

    @Query("select exists(select 1 from books where bookUrl = :bookUrl)")
    fun has(bookUrl: String): Boolean

    @Query("select exists(select 1 from books where name = :name and author = :author)")
    fun has(name: String, author: String): Boolean

    @Query(
        """select exists(select 1 from books where type & ${BookType.local} > 0 
        and (originName = :fileName or (origin != '${BookType.localTag}' and origin like '%' || :fileName)))"""
    )
    fun hasFile(fileName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)

    @Update
    fun update(vararg book: Book)

    @Delete
    fun delete(vararg book: Book)

    @Query("DELETE FROM books")
    fun deleteAll()

    @Transaction
    fun replace(oldBook: Book, newBook: Book) {
        delete(oldBook)
        insert(newBook)
    }

    @Transaction
    fun replaceAll(books: List<Book>) {
        deleteAll()
        if (books.isNotEmpty()) {
            insert(*books.toTypedArray())
        }
    }

    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)

    @Query(
        """update books set lastCheckCount = 0, durChapterIndex = :durChapterIndex, durChapterPos = :durChapterPos, durChapterTime = :durChapterTime where bookUrl = :bookUrl"""
    )
    fun upReadProgress(bookUrl: String, durChapterIndex: Int, durChapterPos: Int, durChapterTime: Long)

    @Query("""UPDATE books SET lastCheckCount = 0, durChapterIndex = :chapterIndex,
        durChapterPos = :chapterPosition, durChapterTitle = :chapterTitle, durChapterTime = :updatedAt
        WHERE bookUrl = :bookUrl""")
    fun upReadAloudProgress(bookUrl: String, chapterIndex: Int, chapterPosition: Int,
        chapterTitle: String?, updatedAt: Long)

    @Query("UPDATE books SET syncTime = :syncTime WHERE bookUrl = :bookUrl AND syncTime < :syncTime")
    fun upSyncTime(bookUrl: String, syncTime: Long)

    @Query("update books set `group` = :newGroupId where `group` = :oldGroupId")
    fun upGroup(oldGroupId: Long, newGroupId: Long)

    @Query("update books set `group` = `group` - :group where `group` & :group > 0")
    fun removeGroup(group: Long)

    @Query("delete from books where type & ${BookType.notShelf} > 0")
    fun deleteNotShelfBook()

    // ── Group preview / count queries (DB-level, replaces in-memory buildGroupPreviewState) ──

    @Query("SELECT COUNT(*) FROM books WHERE $PUBLIC_BOOK_FILTER AND space = :space")
    fun flowAllBookShelfCount(space: Int): Flow<Int>

    @Query(
        """
        SELECT ${BookGroup.IdAll} AS groupId, COUNT(*) AS count FROM books WHERE $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdRoot}, COUNT(*) FROM books
            WHERE type & ${BookType.text} > 0 AND type & ${BookType.local} = 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
            AND (SELECT show FROM book_groups WHERE groupId = ${BookGroup.IdNetNone}) != 1
        UNION ALL SELECT ${BookGroup.IdLocal}, COUNT(*) FROM books WHERE type & ${BookType.local} > 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdAudio}, COUNT(*) FROM books WHERE type & ${BookType.audio} > 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdNetNone}, COUNT(*) FROM books
            WHERE type & ${BookType.audio} = 0 AND type & ${BookType.local} = 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        UNION ALL SELECT ${BookGroup.IdLocalNone}, COUNT(*) FROM books
            WHERE type & ${BookType.local} > 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        UNION ALL SELECT ${BookGroup.IdManga}, COUNT(*) FROM books WHERE type & ${BookType.image} > 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdText}, COUNT(*) FROM books WHERE type & ${BookType.text} > 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdError}, COUNT(*) FROM books WHERE type & ${BookType.updateError} > 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdUnread}, COUNT(*) FROM books WHERE durChapterIndex = 0 AND durChapterPos = 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdReading}, COUNT(*) FROM books WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdReadFinished}, COUNT(*) FROM books WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdReadFinishedUpdate}, COUNT(*) FROM books WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 1 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        UNION ALL SELECT ${BookGroup.IdReadFinishedComplete}, COUNT(*) FROM books WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 0 AND $PUBLIC_BOOK_FILTER
        AND space = :space
        """
    )
    fun flowSystemGroupCounts(space: Int): Flow<List<GroupBookCount>>

    @Query(
        """
        SELECT COUNT(*) FROM books
        WHERE (`group` & :groupId) > 0
        AND ((SELECT isPrivate FROM book_groups WHERE groupId = :groupId) = 1 OR $PUBLIC_BOOK_FILTER)
        AND space = :space
        """
    )
    fun flowUserGroupBookCount(groupId: Long, space: Int): Flow<Int>

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flowGroupPreview(groupId: Long): Flow<List<BookShelfItem>> {
        return MareaSpace.spaceFlow.flatMapLatest { space ->
            when (groupId) {
                BookGroup.IdRoot -> flowBookShelfRootPreview(space)
                BookGroup.IdAll -> flowBookShelfPreview(space)
                BookGroup.IdLocal -> flowBookShelfLocalPreview(space)
                BookGroup.IdAudio -> flowBookShelfAudioPreview(space)
                BookGroup.IdNetNone -> flowBookShelfNetNoGroupPreview(space)
                BookGroup.IdLocalNone -> flowBookShelfLocalNoGroupPreview(space)
                BookGroup.IdManga -> flowBookShelfMangaPreview(space)
                BookGroup.IdText -> flowBookShelfTextPreview(space)
                BookGroup.IdError -> flowBookShelfUpdateErrorPreview(space)
                BookGroup.IdUnread -> flowBookShelfUnreadPreview(space)
                BookGroup.IdReading -> flowBookShelfReadingPreview(space)
                BookGroup.IdReadFinished -> flowBookShelfReadFinishedPreview(space)
                BookGroup.IdReadFinishedUpdate -> flowBookShelfReadFinishedUpdatePreview(space)
                BookGroup.IdReadFinishedComplete -> flowBookShelfReadFinishedCompletePreview(space)
                else -> flowBookShelfPreviewByUserGroup(groupId, space)
            }
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE $PUBLIC_BOOK_FILTER
        AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.text} > 0 AND type & ${BookType.local} = 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
            AND (SELECT show FROM book_groups WHERE groupId = ${BookGroup.IdNetNone}) != 1
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfRootPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.local} > 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfLocalPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.audio} > 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfAudioPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.audio} = 0 AND type & ${BookType.local} = 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfNetNoGroupPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.local} > 0
            AND ($PUBLIC_GROUP_MASK & `group`) = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfLocalNoGroupPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.image} > 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfMangaPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.text} > 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfTextPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE type & ${BookType.updateError} > 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfUpdateErrorPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE durChapterIndex = 0 AND durChapterPos = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfUnreadPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfReadingPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfReadFinishedPreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 1
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfReadFinishedUpdatePreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1 AND canUpdate = 0
            AND $PUBLIC_BOOK_FILTER
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfReadFinishedCompletePreview(space: Int): Flow<List<BookShelfItem>>

    @Query(
        """
        SELECT bookUrl, name, author, origin, originName,
            coverUrl, customCoverUrl, durChapterTitle, durChapterTime,
            durChapterPos, latestChapterTitle, latestChapterTime,
            lastCheckCount, totalChapterNum, durChapterIndex,
            type, `group`, `order`, canUpdate,
            ifnull(customIntro, ifnull(listIntro, intro)) as intro, kind, customTag, wordCount
        FROM books
        WHERE (`group` & :groupId) > 0
            AND ((SELECT isPrivate FROM book_groups WHERE groupId = :groupId) = 1 OR $PUBLIC_BOOK_FILTER)
            AND space = :space
        ORDER BY durChapterTime DESC
        LIMIT 10
        """
    )
    fun flowBookShelfPreviewByUserGroup(groupId: Long, space: Int): Flow<List<BookShelfItem>>

    // ---------- 私密书籍标记 ----------
    // 私密判定取并集：books.isPrivate = 1（单本标记）∪ 所属私密分组。
    // 分组那一半由 PRIVATE_GROUP_MASK 在内存侧按位与得出，避免改动上面十几处投影。

    @Query("SELECT bookUrl FROM books WHERE isPrivate = 1")
    fun flowPrivateBookUrls(): Flow<List<String>>

    /**
     * 私密判定取并集：单本标记 ∪ 所属私密分组；顺带取回分组掩码供分组授权复用。
     *
     * 刻意**不挂起**：AI 工具是同步拼字符串返回的，等不了挂起调用；调用方负责不在主线程调用。
     */
    @Query(
        """
        SELECT
            CASE WHEN
                COALESCE((SELECT isPrivate FROM books WHERE bookUrl = :bookUrl), 0) = 1
                OR ($PRIVATE_GROUP_MASK
                    & COALESCE((SELECT `group` FROM books WHERE bookUrl = :bookUrl), 0)) <> 0
            THEN 1 ELSE 0 END AS isPrivate,
            COALESCE((SELECT `group` FROM books WHERE bookUrl = :bookUrl), 0) AS groupMask
        """
    )
    fun privateFacts(bookUrl: String): PrivateBookFacts

    @Query("UPDATE books SET isPrivate = :isPrivate WHERE bookUrl IN (:bookUrls)")
    suspend fun setBooksPrivate(bookUrls: Set<String>, isPrivate: Boolean)
}
