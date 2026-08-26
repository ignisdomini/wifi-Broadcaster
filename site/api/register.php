<?php
declare(strict_types=1);

/**
 * Регистрация и вход — это одно действие.
 *
 * Приложение присылает позывной и auth_key = sha256(позывной:кодовое слово),
 * посчитанный на телефоне. Свободный позывной занимается, занятый пускает
 * внутрь при верном ключе. Никакого «восстановления доступа» нет и быть не
 * может: кроме кодового слова, о человеке ничего не известно.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Нужен POST', 'METHOD_NOT_ALLOWED', 405);
}

if (!api_token_ok()) {
    json_error('Неверный или отсутствующий токен', 'BAD_TOKEN', 401);
}

$handle = trim((string)($_POST['handle'] ?? ''));
$authKey = trim((string)($_POST['auth_key'] ?? ''));

$handleError = validate_handle($handle);
if ($handleError !== null) {
    json_error($handleError, 'BAD_HANDLE', 422);
}
if (!validate_auth_key($authKey)) {
    json_error('Некорректный ключ доступа', 'BAD_AUTH_KEY', 422);
}

// Ограничение частоты и здесь: иначе позывные можно подбирать перебором.
$perHour = setting_int('rate_limit_per_hour');
if ($perHour > 0) {
    $row = db_one(
        'SELECT COUNT(*) AS c FROM uploads_log WHERE ip_hash = ? AND created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)',
        [ip_hash()]
    );
    if ((int)($row['c'] ?? 0) >= $perHour * 3) {
        json_error('Слишком много попыток. Повторите позже.', 'RATE_LIMIT', 429);
    }
}

try {
    $result = register_or_login($handle, $authKey);
} catch (RuntimeException $e) {
    db_exec('INSERT INTO uploads_log (ip_hash, created_at) VALUES (?, NOW())', [ip_hash()]);
    json_error($e->getMessage(), 'HANDLE_TAKEN', 409);
}

$user = $result['user'];

json_response([
    'ok'           => true,
    'created'      => $result['created'],
    'handle'       => (string)$user['handle'],
    'profile_code' => (string)$user['profile_code'],
    'profile_url'  => profile_url((string)$user['profile_code']),
    'posts_count'  => (int)$user['posts_count'],
]);
