package dto

type PhoneSendCodeRequest struct {
	Phone string `json:"phone"`
}

type PhoneLoginRequest struct {
	Phone string `json:"phone"`
	Code  string `json:"code"`
}
