package main

import (
	"fmt"
	"log"
	"math/rand"
	"prflr"
	"time"
)

func main() {
	pr := &prflr.PRFLR{
		Source: "11msHost",
		Apikey: "PRFLRApiKey@prflr.org:4000",
	}
	err := pr.Setup()
	if err != nil {
		log.Fatal("Connection error")
	}
	pr.Begin("checkUDP")
	for i := 0; i < 10000; i++ {
		timer := fmt.Sprintf("timer.test.%d", rand.Intn(9))
		pr.Begin(timer)
		time.Sleep(1 * time.Second)
		pr.End(timer, fmt.Sprintf("step (%d)", i))
	}
	pr.End("checkUDP", "")
}
