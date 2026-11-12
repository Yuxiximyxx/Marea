package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.help.MareaSpace
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.canSafelyRebindTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookImportRepository(
    private val appDb: AppDatabase,
) {

    // exp.10：导入页的本地书列表按 Marea 当前空间过滤
    @OptIn(ExperimentalCoroutinesApi::class)
    fun flowLocalBooks(): Flow<List<Book>> = MareaSpace.spaceFlow
        .flatMapLatest { space -> appDb.bookDao.flowLocal(space) }
        .flowOn(Dispatchers.IO)

    suspend fun findByFileName(fileName: String): Book? = withContext(Dispatchers.IO) {
        appDb.bookDao.getBookByFileName(fileName)
    }

    suspend fun findAndRebind(fileName: String, filePath: String): Book? =
        withContext(Dispatchers.IO) {
            val book = appDb.bookDao.getBookByFileName(fileName) ?: return@withContext null
            if (book.bookUrl == filePath || !book.canSafelyRebindTo(filePath)) {
                return@withContext book
            }

            // 迁主键后旧目录的正文偏移对新文件没有意义：清掉解析时间让 isLocalModified()
            // 为真，打开这本书时会按新文件重新解析目录，而不是拿旧偏移读取。
            val reboundBook = book.copy(bookUrl = filePath, latestChapterTime = 0)
            appDb.runInTransaction {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookDao.replace(book, reboundBook)
                BookHelp.updateCacheFolder(book, reboundBook)
            }
            reboundBook
        }
}
