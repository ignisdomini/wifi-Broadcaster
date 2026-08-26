<?php
declare(strict_types=1);

function session_start_once(): void
{
    if (session_status() === PHP_SESSION_NONE) {
        session_name('EFIRSESS');
        session_set_cookie_params([
            'httponly' => true,
            'samesite' => 'Lax',
        ]);
        session_start();
    }
}

function admin_is_logged_in(): bool
{
    session_start_once();
    return !empty($_SESSION['efir_admin']);
}

function admin_login(string $password): bool
{
    $hash = (string)setting('admin_password_hash', '');
    if ($hash === '' || !password_verify($password, $hash)) {
        return false;
    }
    session_start_once();
    session_regenerate_id(true);
    $_SESSION['efir_admin'] = true;
    return true;
}

function admin_logout(): void
{
    session_start_once();
    unset($_SESSION['efir_admin']);
    session_destroy();
}

function admin_require(): void
{
    if (!admin_is_logged_in()) {
        header('Location: /admin/');
        exit;
    }
}

function csrf_token(): string
{
    session_start_once();
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(16));
    }
    return (string)$_SESSION['csrf'];
}

function csrf_check(): void
{
    session_start_once();
    $sent = (string)($_POST['csrf'] ?? '');
    $known = (string)($_SESSION['csrf'] ?? '');
    if ($known === '' || !hash_equals($known, $sent)) {
        http_response_code(419);
        exit('Сессия истекла. Обновите страницу и повторите.');
    }
}

/**
 * Проверка токена для API загрузки. Если токен в настройках пуст,
 * загрузка открыта — это осознанный режим по умолчанию, чтобы
 * приложение работало «из коробки».
 */
function api_token_ok(): bool
{
    $expected = trim((string)setting('api_token', ''));
    if ($expected === '') {
        return true;
    }
    $sent = (string)($_SERVER['HTTP_X_EFIR_TOKEN'] ?? $_POST['token'] ?? '');
    return $sent !== '' && hash_equals($expected, $sent);
}
