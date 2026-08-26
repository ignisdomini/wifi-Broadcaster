<?php
declare(strict_types=1);

/**
 * Удаление своей записи из ленты.
 *
 * Проверяется не только ключ, но и принадлежность записи: даже с верным
 * ключом чужое удалить нельзя. Ответ на попытку тронуть чужую запись
 * намеренно такой же, как на несуществующую, — чтобы по нему нельзя было
 * выяснять, какие коды заняты.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Нужен POST', 'METHOD_NOT_ALLOWED', 405);
}

$user = authenticate_request();
if ($user === null) {
    json_error('Нужен ключ владельца', 'UNAUTHORIZED', 401);
}

$code = trim((string)($_POST['code'] ?? ''));
if ($code === '' || !is_valid_code($code)) {
    json_error('Не указан код записи', 'BAD_CODE', 422);
}

$post = db_one(
    'SELECT id FROM posts WHERE code = ? AND user_id = ?',
    [$code, (int)$user['id']]
);

if ($post === null) {
    json_error('Записи нет или она не ваша', 'NOT_FOUND', 404);
}

delete_post((int)$post['id']);
refresh_posts_count((int)$user['id']);

json_response([
    'ok' => true,
    'code' => $code,
    'total' => count_user_posts((int)$user['id']),
]);
