<?php
declare(strict_types=1);

/**
 * Приём передачи из приложения: короткая строка, ушедшая в эфир, и
 * прикреплённый к ней длинный текст.
 *
 * Файлы не принимаются вовсе — ни картинок, ни вложений. Это не ограничение
 * ради экономии, а замысел: текст читается за минуту и не превращает ленту
 * в склад, который потом жалко разбирать.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Нужен POST', 'METHOD_NOT_ALLOWED', 405);
}

// Если тело запроса больше post_max_size, PHP молча обнуляет $_POST.
// Без этой проверки клиент получил бы невнятное «пустая передача».
$contentLength = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
$postMax = php_size_to_bytes((string)ini_get('post_max_size'));
if ($contentLength > 0 && $postMax > 0 && $contentLength > $postMax && empty($_POST)) {
    json_error(
        'Запрос тяжелее, чем разрешает сервер (' . format_bytes($postMax) . ')',
        'REQUEST_TOO_LARGE',
        413
    );
}

if (!api_token_ok()) {
    json_error('Неверный или отсутствующий токен', 'BAD_TOKEN', 401);
}

// --- Ограничение частоты -----------------------------------------------
$perHour = setting_int('rate_limit_per_hour');
if ($perHour > 0) {
    $row = db_one(
        'SELECT COUNT(*) AS c FROM uploads_log WHERE ip_hash = ? AND created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)',
        [ip_hash()]
    );
    if ((int)($row['c'] ?? 0) >= $perHour) {
        json_error(
            'Слишком часто. Лимит — ' . $perHour . ' ' . plural($perHour, 'запись', 'записи', 'записей') . ' в час.',
            'RATE_LIMIT',
            429
        );
    }
}

// --- Кто передаёт -------------------------------------------------------
// Без учётной записи публикация тоже проходит, но передача не попадёт
// ни в чью личную ленту — она останется только на своей странице.
$user = authenticate_request();
$userId = $user !== null ? (int)$user['id'] : null;

$broadcast = trim((string)($_POST['broadcast'] ?? ''));
$body      = trim((string)($_POST['text'] ?? ''));
$channel   = normalize_channel($_POST['channel'] ?? EFIR_CHANNEL_DEFAULT);

// Позывной берём из учётной записи, а не из тела запроса: иначе им можно
// было бы подписаться чужим именем.
$nick = $user !== null
    ? (string)$user['handle']
    : trim((string)($_POST['nick'] ?? ''));

$maxChars = setting_int('max_text_chars');
if (mb_strlen($body) > $maxChars) {
    json_error(
        'Текст длиннее допустимых ' . $maxChars . ' символов',
        'TEXT_TOO_LONG',
        422
    );
}

if (!empty($_FILES)) {
    json_error(
        'Файлы здесь не принимают: в РАДИОИНФОРМАТОРе живёт только текст',
        'FILES_NOT_ACCEPTED',
        422
    );
}

if ($broadcast === '' && $body === '') {
    json_error('Пустая передача: нечего публиковать', 'EMPTY_POST', 422);
}

// --- Сохранение ---------------------------------------------------------
try {
    $created = create_post($broadcast, $body, $nick, $channel, $userId);
    $code = $created['code'];

    if ($userId !== null) {
        refresh_posts_count($userId);
    }

    db_exec('INSERT INTO uploads_log (ip_hash, created_at) VALUES (?, NOW())', [ip_hash()]);
    purge_expired();
} catch (Throwable $e) {
    json_error($e->getMessage(), 'UPLOAD_FAILED', 422);
}

json_response([
    'ok'   => true,
    'code' => $code,
    'url'  => post_url($code),
    'moderation' => setting_int('moderation') === 1,
    'channel' => $channel,
    // Передача попала в личную ленту, только если приложение представилось.
    'in_feed' => $userId !== null,
    'profile_code' => $user !== null ? (string)$user['profile_code'] : null,
]);

// -----------------------------------------------------------------------

function php_size_to_bytes(string $value): int
{
    $value = trim($value);
    if ($value === '') {
        return 0;
    }
    $unit = strtolower($value[strlen($value) - 1]);
    $number = (int)$value;

    return match ($unit) {
        'g' => $number * 1024 * 1024 * 1024,
        'm' => $number * 1024 * 1024,
        'k' => $number * 1024,
        default => $number,
    };
}
