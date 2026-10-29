// Host-side fixtures for the real ES10c list parser. No card or Android runtime required.
#include "euicc/es10c.h"
#include "euicc/euicc.private.h"
#include "euicc/derutil.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint8_t fixture_response[256];
static size_t fixture_length;
static unsigned call_count;
static int first_failure; // 1 = rejected tagList, 2 = transport failure, 3 = both requests rejected

int es10x_command(struct euicc_ctx *ctx, uint8_t **response, unsigned *response_len,
                 const uint8_t *request, unsigned request_len) {
    (void)ctx;
    const uint8_t tagged_request[] = {
        0xBF, 0x2D, 0x0E, 0x5C, 0x0C,
        0x5A, 0x4F, 0x9F, 0x70, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0xB6, 0xB7,
    };
    const uint8_t basic_request[] = {0xBF, 0x2D, 0x00};
    const uint8_t rejected[] = {0xBF, 0x2D, 0x03, 0x81, 0x01, 0x01};
    call_count++;
    if (call_count == 1) {
        assert(request_len == sizeof(tagged_request));
        assert(memcmp(request, tagged_request, request_len) == 0);
    } else {
        assert(call_count == 2);
        assert(request_len == sizeof(basic_request));
        assert(memcmp(request, basic_request, request_len) == 0);
    }
    if (first_failure == 2 && call_count == 1) return -1;
    const uint8_t *payload = fixture_response;
    size_t payload_len = fixture_length;
    if ((first_failure == 1 && call_count == 1) || first_failure == 3) {
        payload = rejected;
        payload_len = sizeof(rejected);
    }
    *response = malloc(payload_len);
    assert(*response);
    memcpy(*response, payload, payload_len);
    *response_len = (unsigned)payload_len;
    return 0;
}

void euicc_apdu_unhandled_tag_print(FILE *fp, const struct euicc_derutil_node *node) {
    (void)fp;
    (void)node;
}

static size_t wrap(uint8_t *output, uint8_t tag, const uint8_t *value, size_t length) {
    assert(length < 128);
    output[0] = tag;
    output[1] = (uint8_t)length;
    memcpy(output + 2, value, length);
    return length + 2;
}

static void set_fixture(const uint8_t *metadata, size_t metadata_len) {
    // Real ProfileInfo TLVs retain identifiers, name, nickname, carrier, state,
    // class and icon while optional owner fields vary between test cases.
    const uint8_t fields[] = {
        0x5A, 0x0A, 0x98, 0x68, 0, 0, 0, 0, 0, 0, 0, 0x10,
        0x4F, 0x02, 0xA0, 0x00,
        0x9F, 0x70, 0x01, 0x01,
        0x90, 0x04, 'N', 'i', 'c', 'k',
        0x91, 0x07, 'C', 'a', 'r', 'r', 'i', 'e', 'r',
        0x92, 0x04, 'N', 'a', 'm', 'e',
        0x93, 0x01, 0x01,
        0x94, 0x03, 0x01, 0x02, 0x03,
        0x95, 0x01, 0x02,
    };
    uint8_t body[192], profile[196], list[200];
    memcpy(body, fields, sizeof(fields));
    if (metadata_len) memcpy(body + sizeof(fields), metadata, metadata_len);
    size_t profile_len = wrap(profile, 0xE3, body, sizeof(fields) + metadata_len);
    size_t list_len = wrap(list, 0xA0, profile, profile_len);
    fixture_response[0] = 0xBF;
    fixture_response[1] = 0x2D;
    fixture_response[2] = (uint8_t)list_len;
    memcpy(fixture_response + 3, list, list_len);
    fixture_length = list_len + 3;
    call_count = 0;
    first_failure = 0;
}

static void check_list(const char *owner, unsigned expected_calls) {
    struct euicc_ctx ctx = {0};
    struct es10c_profile_info_list *head = NULL;
    assert(es10c_get_profiles_info(&ctx, &head) == 0);
    assert(call_count == expected_calls);
    assert(head && head->next == NULL);
    assert(strcmp(head->iccid, "89860000000000000001") == 0);
    assert(strcmp(head->isdpAid, "a000") == 0);
    assert(strcmp(head->profileName, "Name") == 0);
    assert(strcmp(head->profileNickname, "Nick") == 0);
    assert(strcmp(head->serviceProviderName, "Carrier") == 0);
    assert(head->profileState == ES10C_PROFILE_STATE_ENABLED);
    assert(head->profileClass == ES10C_PROFILE_CLASS_OPERATIONAL);
    assert(head->iconType == ES10C_ICON_TYPE_PNG);
    assert(strcmp(head->icon, "AQID") == 0);
    if (owner) {
        assert(head->profileOwner.mccmnc && strcmp(head->profileOwner.mccmnc, owner) == 0);
    } else {
        assert(head->profileOwner.mccmnc == NULL);
    }
    es10c_profile_info_list_free_all(head);
}

int main(void) {
    const uint8_t valid[] = {0xB7, 0x05, 0x80, 0x03, 0x64, 0xF0, 0x10};
    const uint8_t empty_owner[] = {0xB7, 0x00};
    const uint8_t unknown_child[] = {0xB7, 0x05, 0x81, 0x03, 0x64, 0xF0, 0x10};
    const uint8_t short_owner[] = {0xB7, 0x04, 0x80, 0x02, 0x64, 0xF0};
    const uint8_t long_owner[] = {0xB7, 0x06, 0x80, 0x04, 0x64, 0xF0, 0x10, 0};
    const uint8_t truncated_child[] = {0xB7, 0x04, 0x80, 0x03, 0x64, 0xF0};
    const uint8_t duplicate[] = {
        0xB7, 0x05, 0x80, 0x03, 0x64, 0xF0, 0x10,
        0xB7, 0x0A, 0x80, 0x03, 0x13, 0x00, 0x62, 0x80, 0x03, 0x54, 0xF4, 0x00,
    };
    const uint8_t invalid_then_valid[] = {
        0xB7, 0x04, 0x80, 0x02, 0x64, 0xF0,
        0xB7, 0x05, 0x80, 0x03, 0x13, 0x00, 0x62,
    };
    set_fixture(valid, sizeof(valid)); check_list("64f010", 1);
    set_fixture(NULL, 0); check_list(NULL, 1);
    set_fixture(empty_owner, sizeof(empty_owner)); check_list(NULL, 1);
    set_fixture(unknown_child, sizeof(unknown_child)); check_list(NULL, 1);
    set_fixture(short_owner, sizeof(short_owner)); check_list(NULL, 1);
    set_fixture(long_owner, sizeof(long_owner)); check_list(NULL, 1);
    set_fixture(truncated_child, sizeof(truncated_child)); check_list(NULL, 1);
    set_fixture(duplicate, sizeof(duplicate)); check_list("64f010", 1);
    set_fixture(invalid_then_valid, sizeof(invalid_then_valid)); check_list("130062", 1);
    set_fixture(NULL, 0); first_failure = 1; check_list(NULL, 2);
    set_fixture(NULL, 0); first_failure = 2; check_list(NULL, 2);
    set_fixture(NULL, 0); first_failure = 3;
    struct euicc_ctx ctx = {0};
    struct es10c_profile_info_list *head = NULL;
    assert(es10c_get_profiles_info(&ctx, &head) < 0);
    assert(call_count == 2 && head == NULL);
    puts("ES10c profile owner fixtures: 12 cases passed");
    return 0;
}
