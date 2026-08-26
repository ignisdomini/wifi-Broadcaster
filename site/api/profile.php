<?php
declare(strict_types=1);

/**
 * Визитка владельца ключа: чтение и правка.
 *
 * Править её можно только из приложения. На сайте формы нет намеренно: у
 * человека здесь нет ни пароля, ни входа — есть кодовое слово, которое телефон
 * никуда не отдаёт. Заодно это отсекает и чужие руки: подобрать код ленты
 * нельзя, а без ключа он всё равно ничего не даёт.
 *
 * GET  — вернуть текущие значения.
 * POST — сохранить присланные; отсутствующее поле трактуется как пустое,
 *        чтобы приложение могло стереть строку, просто не прислав её.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

$user = authenticate_request();
if ($user === null) {
    json_error('Нужен ключ владельца', 'UNAUTHORIZED', 401);
}

$userId = (int)$user['id'];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_response([
        'ok' => true,
        'handle' => (string)$user['handle'],
        'profile_url' => profile_url((string)$user['profile_code']),
        'public' => (int)($user['contacts_public'] ?? 1) === 1,
        'contacts' => user_contacts($user),
    ]);
}

$values = [];
foreach (contact_field_keys() as $key) {
    $values[$key] = normalize_contact_value($key, (string)($_POST[$key] ?? ''));
}

$errors = validate_contacts($values);
if ($errors) {
    json_error(implode('. ', $errors), 'INVALID', 422);
}

// Рубильник по умолчанию включён: тот, кто вообще открыл этот экран и
// что-то заполнил, хочет, чтобы это было видно.
$public = !isset($_POST['public']) || in_array((string)$_POST['public'], ['1', 'true', 'yes'], true);

save_contacts($userId, $values, $public);

$fresh = find_user_by_id($userId) ?? $user;

json_response([
    'ok' => true,
    'public' => (int)($fresh['contacts_public'] ?? 1) === 1,
    'contacts' => user_contacts($fresh),
    'profile_url' => profile_url((string)$fresh['profile_code']),
]);
