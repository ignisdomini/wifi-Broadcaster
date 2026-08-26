<?php
declare(strict_types=1);

/**
 * Передачи: создание, выборка, удаление.
 *
 * Прикреплять файлы нельзя намеренно. Здесь ходит только текст: он лёгкий,
 * читается за минуту и не превращает ленту в склад. Раз нет загрузок — нет
 * ни каталога с картинками, ни обработки, ни разрастающегося хранилища.
 */

function create_post(
    string $broadcast,
    string $body,
    string $nick,
    int $channel = EFIR_CHANNEL_DEFAULT,
    ?int $userId = null
): array {
    // Коллизия кода маловероятна (31^5 ≈ 28 млн), но проверить дешевле, чем ловить.
    for ($attempt = 0; $attempt < 8; $attempt++) {
        $code = generate_code();
        $exists = db_one('SELECT id FROM posts WHERE code = ?', [$code]);
        if ($exists === null) {
            break;
        }
        $code = null;
    }
    if (empty($code)) {
        throw new RuntimeException('Не удалось подобрать короткий код');
    }

    db_exec(
        'INSERT INTO posts (code, user_id, nick, channel, broadcast, body, is_hidden, ip_hash, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())',
        [
            $code,
            $userId,
            mb_substr($nick, 0, 64),
            normalize_channel($channel),
            mb_substr($broadcast, 0, 255),
            $body,
            setting_int('moderation') === 1 ? 1 : 0,
            ip_hash(),
        ]
    );

    $id = (int)db()->lastInsertId();
    return ['id' => $id, 'code' => $code];
}

function find_post_by_code(string $code, bool $includeHidden = false): ?array
{
    $sql = 'SELECT * FROM posts WHERE code = ?';
    if (!$includeHidden) {
        $sql .= ' AND is_hidden = 0';
    }
    return db_one($sql, [$code]);
}

function count_posts(bool $onlyVisible = true): int
{
    $sql = 'SELECT COUNT(*) AS c FROM posts' . ($onlyVisible ? ' WHERE is_hidden = 0' : '');
    $row = db_one($sql);
    return (int)($row['c'] ?? 0);
}

/**
 * Просмотры считаются, но наружу не показываются: читателю это число ничего
 * не даёт, а автора превращает в наблюдателя за счётчиком. Видит только
 * администратор — ему нужно понимать нагрузку.
 */
function increment_views(int $postId): void
{
    db_exec('UPDATE posts SET views = views + 1 WHERE id = ?', [$postId]);
}

function delete_post(int $postId): void
{
    db_exec('DELETE FROM posts WHERE id = ?', [$postId]);
}

/**
 * Представление передачи для шаблонов.
 * Здесь же режем длинный текст до анонса — целиком он нужен только на своей странице.
 */
function post_public_view(array $post): array
{
    $body = (string)$post['body'];
    $excerpt = mb_substr(trim(preg_replace('/\s+/u', ' ', $body) ?? ''), 0, 180);
    if (mb_strlen($body) > 180) {
        $excerpt .= '…';
    }

    $channel = normalize_channel($post['channel'] ?? EFIR_CHANNEL_DEFAULT);

    return [
        'id'           => (int)$post['id'],
        'code'         => (string)$post['code'],
        'nick'         => (string)$post['nick'],
        'broadcast'    => (string)$post['broadcast'],
        'excerpt'      => $excerpt,
        'has_body'     => trim($body) !== '',
        'channel'      => $channel,
        'created_at'   => (string)$post['created_at'],
        'created_human'=> human_time((string)$post['created_at']),
        'url'          => post_url((string)$post['code']),
        // Ссылки на автора здесь намеренно нет: связь заводится только через эфир.
    ];
}

/**
 * Чистка по сроку хранения. Вызывается лениво из API загрузки.
 * Забывать старое — часть замысла, а не уборка ради места.
 */
function purge_expired(): void
{
    $days = setting_int('retention_days');
    if ($days <= 0) {
        return;
    }
    $old = db_all(
        'SELECT id FROM posts WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY) LIMIT 50',
        [$days]
    );
    foreach ($old as $row) {
        delete_post((int)$row['id']);
    }
}
